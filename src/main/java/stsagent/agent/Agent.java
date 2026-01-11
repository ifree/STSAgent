package stsagent.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsagent.config.AgentConfig;
import stsagent.llm.LLMClient;
import stsagent.llm.LLMMessage;
import stsagent.mcp.MCPClient;
import stsagent.tools.BuiltinTools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Unified Agent for Slay the Spire.
 * Combines state reading (builtin tools) with action execution (MCP).
 */
public class Agent {
    private static final Logger logger = LogManager.getLogger(Agent.class);
    private static final int MAX_ITERATIONS = 20;

    // Chat history management
    private static final int MAX_HISTORY_MESSAGES = 16;  // Trigger summarization threshold
    private static final int KEEP_RECENT_MESSAGES = 4;   // Keep recent messages after summarization

    private final LLMClient llmClient;
    private final MCPClient mcpClient;
    private final BuiltinTools builtinTools;
    private final AgentConfig config;

    // Persistent chat history (thread-safe access via synchronized)
    private final List<LLMMessage> chatHistory = new ArrayList<>();
    private String historySummary = null;  // Summarized history
    private final Object historyLock = new Object();  // Lock for thread-safe access

    private volatile boolean running = false;
    private volatile boolean stopRequested = false;

    public Agent(LLMClient llmClient, MCPClient mcpClient, AgentConfig config) {
        this.llmClient = llmClient;
        this.mcpClient = mcpClient;
        this.builtinTools = new BuiltinTools();
        this.config = config;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        stopRequested = true;
    }

    /**
     * Analyze current game state (read-only, no actions).
     */
    public void analyze(Consumer<String> onOutput, Runnable onComplete) {
        // Atomically check and set running flag to prevent race condition
        synchronized (this) {
            if (!checkReady(onOutput, onComplete)) return;
            running = true;
            stopRequested = false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                runLoop(Mode.ANALYZE, null, onOutput);
            } catch (Exception e) {
                logger.error("Analyze error", e);
                onOutput.accept("\n[Error: " + e.getMessage() + "]");
            } finally {
                running = false;
                onComplete.run();
            }
        });
    }

    /**
     * Play the game autonomously (REACT loop with actions).
     */
    public void play(Consumer<String> onOutput, Runnable onComplete) {
        // Atomically check and set running flag to prevent race condition
        synchronized (this) {
            if (!checkReady(onOutput, onComplete)) return;

            // Check MCP availability for play mode
            if (!mcpClient.isAvailable()) {
                onOutput.accept("MCP server not available. Start MCPTheSpire first!");
                onComplete.run();
                return;
            }

            running = true;
            stopRequested = false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                runLoop(Mode.PLAY, null, onOutput);
            } catch (Exception e) {
                logger.error("Play error", e);
                onOutput.accept("\n[Error: " + e.getMessage() + "]");
            } finally {
                running = false;
                onComplete.run();
            }
        });
    }

    /**
     * Answer a question about the game.
     */
    public void chat(String question, Consumer<String> onOutput, Runnable onComplete) {
        // Atomically check and set running flag to prevent race condition
        synchronized (this) {
            if (!checkReady(onOutput, onComplete)) return;
            running = true;
            stopRequested = false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                runLoop(Mode.CHAT, question, onOutput);
            } catch (Exception e) {
                logger.error("Chat error", e);
                onOutput.accept("\n[Error: " + e.getMessage() + "]");
            } finally {
                running = false;
                onComplete.run();
            }
        });
    }

    private boolean checkReady(Consumer<String> onOutput, Runnable onComplete) {
        if (running) {
            onOutput.accept("Agent is already running...");
            onComplete.run();
            return false;
        }
        if (!config.hasApiKey()) {
            onOutput.accept("API key not configured. Edit config file.");
            onComplete.run();
            return false;
        }
        return true;
    }

    private enum Mode {
        ANALYZE,  // Read-only analysis
        PLAY,     // Full REACT loop with actions
        CHAT      // Answer questions
    }

    private void runLoop(Mode mode, String userInput, Consumer<String> onOutput) {
        // Build tool list
        JsonArray tools = buildToolList(mode);

        // Build messages based on mode (all modes include history for context)
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(buildSystemPrompt(mode)));

        // Inject history context for all modes
        synchronized (historyLock) {
            if (historySummary != null) {
                messages.add(LLMMessage.system("Previous conversation summary:\n" + historySummary));
            }
            // Add recent chat history so LLM knows what was discussed
            if (!chatHistory.isEmpty()) {
                messages.addAll(chatHistory);
            }
        }

        // Add user prompt based on mode
        messages.add(LLMMessage.user(buildUserPrompt(mode, userInput)));

        String lastAssistantContent = null;

        int iterations = 0;
        while (iterations < MAX_ITERATIONS && !stopRequested) {
            iterations++;

            try {
                // Call LLM with tools
                LLMClient.ChatResponse response = llmClient.chatWithTools(messages, tools);

                // Output content
                if (response.content != null && !response.content.isEmpty()) {
                    onOutput.accept(response.content);
                    lastAssistantContent = response.content;
                }

                // No tool calls = done
                if (!response.hasToolCalls()) {
                    logger.info("Agent finished after {} iterations", iterations);
                    break;
                }

                // Add assistant message with tool calls
                messages.add(LLMMessage.assistantWithToolCalls(response.toolCalls));

                // Execute tool calls
                for (LLMClient.ToolCall toolCall : response.toolCalls) {
                    String result = executeToolCall(mode, toolCall, onOutput);
                    messages.add(LLMMessage.toolResponse(toolCall.id, result));
                }

            } catch (Exception e) {
                logger.error("Error in agent loop", e);
                onOutput.accept("\n[Error: " + e.getMessage() + "]");
                break;
            }
        }

        if (iterations >= MAX_ITERATIONS) {
            onOutput.accept("\n[Reached max iterations]");
        }

        // Save to history for continuity
        if (lastAssistantContent != null) {
            String userMessage;
            switch (mode) {
                case ANALYZE:
                    userMessage = "[User requested game state analysis]";
                    break;
                case PLAY:
                    userMessage = "[User requested AI to play]";
                    break;
                case CHAT:
                default:
                    userMessage = userInput;
                    break;
            }

            boolean needsSummarization = false;
            synchronized (historyLock) {
                chatHistory.add(LLMMessage.user(userMessage));
                chatHistory.add(LLMMessage.assistant(lastAssistantContent));
                logger.info("Chat history size: {} messages", chatHistory.size());
                needsSummarization = chatHistory.size() >= MAX_HISTORY_MESSAGES;
            }

            // Summarization outside lock to avoid blocking during network call
            if (needsSummarization) {
                summarizeChatHistory();
            }
        }
    }

    /**
     * Summarize chat history to reduce token usage.
     * Handles its own locking to avoid holding lock during network call.
     */
    private void summarizeChatHistory() {
        StringBuilder historyText = new StringBuilder();
        int keptMessageCount;

        // Step 1: Collect data and trim history within lock
        synchronized (historyLock) {
            if (chatHistory.size() < KEEP_RECENT_MESSAGES * 2) {
                return;
            }

            logger.info("Summarizing chat history ({} messages)...", chatHistory.size());

            if (historySummary != null) {
                historyText.append("Previous summary:\n").append(historySummary).append("\n\n");
            }
            historyText.append("Recent conversation:\n");

            int summarizeEnd = chatHistory.size() - KEEP_RECENT_MESSAGES;
            for (int i = 0; i < summarizeEnd; i++) {
                LLMMessage msg = chatHistory.get(i);
                String role = msg.getRole().equals("user") ? "User" : "AI";
                String content = msg.getContent();
                historyText.append(role).append(": ").append(content != null ? content : "").append("\n");
            }

            // Keep recent messages
            List<LLMMessage> recentMessages = new ArrayList<>(
                chatHistory.subList(summarizeEnd, chatHistory.size())
            );
            chatHistory.clear();
            chatHistory.addAll(recentMessages);
            keptMessageCount = chatHistory.size();
        }

        // Step 2: Call LLM for summarization OUTSIDE lock
        try {
            List<LLMMessage> summaryRequest = new ArrayList<>();
            summaryRequest.add(LLMMessage.system(
                "Summarize the following conversation concisely. " +
                "Capture key topics discussed, important information shared, and any decisions made. " +
                "Keep it under 200 words. Output only the summary, no extra text."
            ));
            summaryRequest.add(LLMMessage.user(historyText.toString()));

            String newSummary = llmClient.chat(summaryRequest);

            // Step 3: Update summary within lock
            synchronized (historyLock) {
                historySummary = newSummary;
            }

            logger.info("Summarization complete. Kept {} recent messages.", keptMessageCount);

        } catch (Exception e) {
            logger.error("Failed to summarize history", e);
            // History already trimmed, just log the error
        }
    }

    /**
     * Clear chat history and summary.
     */
    public void clearChatHistory() {
        synchronized (historyLock) {
            chatHistory.clear();
            historySummary = null;
        }
        logger.info("Chat history cleared");
    }

    private JsonArray buildToolList(Mode mode) {
        JsonArray tools = new JsonArray();

        // Always include builtin tools (state reading)
        for (int i = 0; i < builtinTools.getToolDefinitions().size(); i++) {
            tools.add(builtinTools.getToolDefinitions().get(i));
        }

        // Only include execute_actions in PLAY mode
        if (mode == Mode.PLAY) {
            tools.add(mcpClient.getExecuteActionsTool());
        }

        return tools;
    }

    private String buildSystemPrompt(Mode mode) {
        String basePrompt = config.getSystemPrompt();

        switch (mode) {
            case ANALYZE:
                return basePrompt + "\n\n" + config.getAnalyzePrompt();
            case PLAY:
                return basePrompt + "\n\n" + config.getPlayPrompt();
            case CHAT:
            default:
                return basePrompt;
        }
    }

    private String buildUserPrompt(Mode mode, String userInput) {
        switch (mode) {
            case ANALYZE:
                return "Analyze the current game state and provide strategic advice. " +
                       "Use the state query tools to understand the situation.";
            case PLAY:
                return "Play the game. First use state query tools to understand the situation, " +
                       "then use execute_actions to play. Say 'done' when finished.";
            case CHAT:
                return userInput != null ? userInput : "Hello";
            default:
                return "";
        }
    }

    private String executeToolCall(Mode mode, LLMClient.ToolCall toolCall, Consumer<String> onOutput) {
        String toolName = toolCall.name;
        JsonObject args = toolCall.arguments;

        logger.info("Executing tool: {} with args: {}", toolName, args);

        // Check if it's a builtin tool
        if (builtinTools.isBuiltinTool(toolName)) {
            String result = builtinTools.execute(toolName, args);
            logger.debug("Builtin tool result: {}", result);
            return result;
        }

        // MCP tool (execute_actions)
        if (MCPClient.EXECUTE_ACTIONS.equals(toolName)) {
            // In ANALYZE mode, block action execution
            if (mode == Mode.ANALYZE) {
                return "{\"error\": \"Action execution not allowed in analyze mode\"}";
            }

            onOutput.accept("\n[Executing actions...] ");

            if (args.has("actions") && args.get("actions").isJsonArray()) {
                JsonArray actions = args.getAsJsonArray("actions");
                MCPClient.ToolResult result = mcpClient.executeActions(actions);
                String resultStr = result.toString();
                onOutput.accept(resultStr);
                return resultStr;
            } else {
                return "{\"error\": \"Missing 'actions' parameter\"}";
            }
        }

        return "{\"error\": \"Unknown tool: " + toolName + "\"}";
    }

    /**
     * Get quick combat tip (simplified single-turn analysis).
     */
    public CompletableFuture<String> getQuickTip() {
        // Check if already running
        if (running) {
            return CompletableFuture.completedFuture("Agent is busy...");
        }

        return CompletableFuture.supplyAsync(() -> {
            // Double-check and set running flag
            synchronized (this) {
                if (running) {
                    return "Agent is busy...";
                }
                running = true;
            }

            try {
                if (!config.hasApiKey()) {
                    return "API key not configured.";
                }
                if (!builtinTools.getReader().isInCombat()) {
                    return "Not in combat.";
                }

                // Get combat state directly
                String combatState = builtinTools.execute(BuiltinTools.GET_COMBAT_STATE, null);

                // Simple prompt for quick tip
                List<LLMMessage> messages = new ArrayList<>();
                messages.add(LLMMessage.system(
                        "You are a Slay the Spire expert. Give a VERY brief suggestion (1-2 sentences) " +
                        "for what cards to play this turn. Focus on the most important action."
                ));
                messages.add(LLMMessage.user("Combat state:\n" + combatState + "\n\nWhat should I play?"));

                String tip = llmClient.chat(messages);

                // Save tip to history for context continuity
                if (tip != null && !tip.startsWith("Error:")) {
                    boolean needsSummarization = false;
                    synchronized (historyLock) {
                        chatHistory.add(LLMMessage.user("[User requested quick combat tip]"));
                        chatHistory.add(LLMMessage.assistant(tip));
                        logger.info("Tip saved to history. Size: {} messages", chatHistory.size());
                        needsSummarization = chatHistory.size() >= MAX_HISTORY_MESSAGES;
                    }

                    if (needsSummarization) {
                        summarizeChatHistory();
                    }
                }

                return tip;
            } catch (Exception e) {
                logger.error("Error getting quick tip", e);
                return "Error: " + e.getMessage();
            } finally {
                running = false;
            }
        });
    }

    /**
     * Check if in game.
     */
    public boolean isInGame() {
        return builtinTools.getReader().isInGame();
    }

    /**
     * Check if in combat.
     */
    public boolean isInCombat() {
        return builtinTools.getReader().isInCombat();
    }
}
