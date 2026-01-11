package stsagent.config;

import com.badlogic.gdx.Input;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;

/**
 * Configuration for STS Agent.
 */
public class AgentConfig {
    private static final Logger logger = LogManager.getLogger(AgentConfig.class);
    private static final String MOD_NAME = "STSAgent";

    // LLM Configuration
    private String llmApiKey = "";
    private String llmBaseUrl = "https://api.openai.com/v1";
    private String llmModel = "gpt-4o-mini";

    // MCP Configuration
    private String mcpServerUrl = "http://127.0.0.1:8080";

    // UI Configuration
    private float overlayOpacity = 0.85f;

    // Hotkey Configuration
    private int toggleKey = Input.Keys.F8;
    private int analyzeKey = Input.Keys.F9;

    // Prompt Configuration
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    private String analyzePrompt = DEFAULT_ANALYZE_PROMPT;
    private String playPrompt = DEFAULT_PLAY_PROMPT;

    // Default prompts
    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are an expert Slay the Spire AI assistant.\\n" +
            "You have access to tools that can read the game state and execute actions.\\n" +
            "All card and enemy indices are 1-based (first = 1).\\n" +
            "Attack cards require target_index to specify which enemy to attack.";

    private static final String DEFAULT_ANALYZE_PROMPT =
            "ANALYZE MODE: Provide strategic advice WITHOUT executing actions.\\n" +
            "Use state query tools to understand the situation, then give recommendations.\\n" +
            "Consider: energy efficiency, enemy intents, card synergies, relic effects.\\n" +
            "Be concise but actionable. Start with your recommendation.";

    private static final String DEFAULT_PLAY_PROMPT =
            "PLAY MODE: Play the game using available tools.\\n" +
            "1. First use get_combat_state or get_screen to understand the situation\\n" +
            "2. Then use execute_actions to play cards and make decisions\\n" +
            "3. In combat: play cards efficiently, use all energy, then end_turn\\n" +
            "4. Outside combat: use choose() for options, proceed() to continue\\n" +
            "Say 'done' when you've completed your turn or action.";

    private SpireConfig config;

    public AgentConfig() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            Properties defaults = new Properties();
            defaults.setProperty("llm.apiKey", llmApiKey);
            defaults.setProperty("llm.baseUrl", llmBaseUrl);
            defaults.setProperty("llm.model", llmModel);
            defaults.setProperty("mcp.serverUrl", mcpServerUrl);
            defaults.setProperty("ui.overlayOpacity", String.valueOf(overlayOpacity));
            defaults.setProperty("hotkey.toggle", String.valueOf(toggleKey));
            defaults.setProperty("hotkey.analyze", String.valueOf(analyzeKey));
            defaults.setProperty("prompt.system", systemPrompt);
            defaults.setProperty("prompt.analyze", analyzePrompt);
            defaults.setProperty("prompt.play", playPrompt);

            config = new SpireConfig(MOD_NAME, "config", defaults);

            llmApiKey = config.getString("llm.apiKey");
            llmBaseUrl = config.getString("llm.baseUrl");
            llmModel = config.getString("llm.model");
            mcpServerUrl = config.getString("mcp.serverUrl");
            overlayOpacity = config.getFloat("ui.overlayOpacity");

            String toggleStr = config.getString("hotkey.toggle");
            String analyzeStr = config.getString("hotkey.analyze");
            toggleKey = parseKeyCode(toggleStr, Input.Keys.F8);
            analyzeKey = parseKeyCode(analyzeStr, Input.Keys.F9);

            systemPrompt = parsePrompt(config.getString("prompt.system"), DEFAULT_SYSTEM_PROMPT);
            analyzePrompt = parsePrompt(config.getString("prompt.analyze"), DEFAULT_ANALYZE_PROMPT);
            playPrompt = parsePrompt(config.getString("prompt.play"), DEFAULT_PLAY_PROMPT);

            logger.info("=== STSAgent Config Loaded ===");
            logger.info("LLM: {} @ {}", llmModel, llmBaseUrl);
            logger.info("MCP: {}", mcpServerUrl);
            logger.info("Toggle: {} | Analyze: {}", getKeyName(toggleKey), getKeyName(analyzeKey));
            logger.info("==============================");

        } catch (IOException e) {
            logger.error("Failed to load config, using defaults", e);
        }
    }

    public void saveConfig() {
        try {
            config.setString("llm.apiKey", llmApiKey);
            config.setString("llm.baseUrl", llmBaseUrl);
            config.setString("llm.model", llmModel);
            config.setString("mcp.serverUrl", mcpServerUrl);
            config.setFloat("ui.overlayOpacity", overlayOpacity);
            config.setInt("hotkey.toggle", toggleKey);
            config.setInt("hotkey.analyze", analyzeKey);
            config.setString("prompt.system", systemPrompt.replace("\n", "\\n"));
            config.setString("prompt.analyze", analyzePrompt.replace("\n", "\\n"));
            config.setString("prompt.play", playPrompt.replace("\n", "\\n"));
            config.save();
            logger.info("Config saved");
        } catch (IOException e) {
            logger.error("Failed to save config", e);
        }
    }

    // ========== Getters and Setters ==========

    public String getLlmApiKey() { return llmApiKey; }
    public void setLlmApiKey(String v) { this.llmApiKey = v; }

    public String getLlmBaseUrl() { return llmBaseUrl; }
    public void setLlmBaseUrl(String v) { this.llmBaseUrl = v; }

    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String v) { this.llmModel = v; }

    public String getMcpServerUrl() { return mcpServerUrl; }
    public void setMcpServerUrl(String v) { this.mcpServerUrl = v; }

    public float getOverlayOpacity() { return overlayOpacity; }
    public void setOverlayOpacity(float v) { this.overlayOpacity = v; }

    public int getToggleKey() { return toggleKey; }
    public void setToggleKey(int v) { this.toggleKey = v; }

    public int getAnalyzeKey() { return analyzeKey; }
    public void setAnalyzeKey(int v) { this.analyzeKey = v; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String v) { this.systemPrompt = v; }

    public String getAnalyzePrompt() { return analyzePrompt; }
    public void setAnalyzePrompt(String v) { this.analyzePrompt = v; }

    public String getPlayPrompt() { return playPrompt; }
    public void setPlayPrompt(String v) { this.playPrompt = v; }

    public boolean hasApiKey() {
        return llmApiKey != null && !llmApiKey.trim().isEmpty();
    }

    public String getToggleKeyName() { return getKeyName(toggleKey); }
    public String getAnalyzeKeyName() { return getKeyName(analyzeKey); }

    // ========== Helper Methods ==========

    private int parseKeyCode(String value, int defaultKey) {
        if (value == null || value.trim().isEmpty()) {
            return defaultKey;
        }
        value = value.trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            int keyCode = Input.Keys.valueOf(value.toUpperCase());
            if (keyCode != -1) {
                return keyCode;
            }
            logger.warn("Unknown key '{}', using default", value);
            return defaultKey;
        }
    }

    private static String getKeyName(int keyCode) {
        if (keyCode >= Input.Keys.F1 && keyCode <= Input.Keys.F12) {
            return "F" + (keyCode - Input.Keys.F1 + 1);
        }
        String name = Input.Keys.toString(keyCode);
        return name != null ? name : "Key" + keyCode;
    }

    private String parsePrompt(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue.replace("\\n", "\n");
        }
        return value.replace("\\n", "\n");
    }
}
