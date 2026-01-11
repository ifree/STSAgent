package stsagent.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Represents a message in the LLM conversation.
 * Supports tool calls and tool responses.
 */
public class LLMMessage {
    private String role;
    private String content;
    private List<LLMClient.ToolCall> toolCalls; // For assistant messages with tool calls
    private String toolCallId; // For tool response messages

    public LLMMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static LLMMessage system(String content) {
        return new LLMMessage("system", content);
    }

    public static LLMMessage user(String content) {
        return new LLMMessage("user", content);
    }

    public static LLMMessage assistant(String content) {
        return new LLMMessage("assistant", content);
    }

    /**
     * Create an assistant message that contains tool calls.
     */
    public static LLMMessage assistantWithToolCalls(List<LLMClient.ToolCall> toolCalls) {
        LLMMessage msg = new LLMMessage("assistant", null);
        msg.toolCalls = toolCalls;
        return msg;
    }

    /**
     * Create a tool response message.
     */
    public static LLMMessage toolResponse(String toolCallId, String content) {
        LLMMessage msg = new LLMMessage("tool", content);
        msg.toolCallId = toolCallId;
        return msg;
    }

    /**
     * Convert this message to JSON for the API request.
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);

        if (content != null) {
            obj.addProperty("content", content);
        }

        // For assistant messages with tool calls
        if (toolCalls != null && !toolCalls.isEmpty()) {
            JsonArray toolCallsArray = new JsonArray();
            for (LLMClient.ToolCall tc : toolCalls) {
                JsonObject tcObj = new JsonObject();
                tcObj.addProperty("id", tc.id);
                tcObj.addProperty("type", "function");
                JsonObject funcObj = new JsonObject();
                funcObj.addProperty("name", tc.name);
                funcObj.addProperty("arguments", tc.arguments.toString());
                tcObj.add("function", funcObj);
                toolCallsArray.add(tcObj);
            }
            obj.add("tool_calls", toolCallsArray);
        }

        // For tool response messages
        if (toolCallId != null) {
            obj.addProperty("tool_call_id", toolCallId);
        }

        return obj;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<LLMClient.ToolCall> getToolCalls() { return toolCalls; }
    public String getToolCallId() { return toolCallId; }
}
