package stsagent.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified MCP Client for MCPTheSpire.
 * Only handles execute_actions tool for game control.
 */
public class MCPClient {
    private static final Logger logger = LogManager.getLogger(MCPClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static final String EXECUTE_ACTIONS = "execute_actions";

    private final OkHttpClient client;
    private final Gson gson;
    private final AtomicInteger requestId;

    private String baseUrl;
    private String sessionId;
    private boolean initialized = false;

    public MCPClient(String baseUrl) {
        this.baseUrl = normalizeUrl(baseUrl);
        this.gson = new Gson();
        this.requestId = new AtomicInteger(1);

        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Check if MCP server is available.
     */
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/health")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Initialize MCP connection.
     */
    public boolean initialize() {
        try {
            JsonObject params = new JsonObject();
            params.addProperty("protocolVersion", "2024-11-05");

            JsonObject clientInfo = new JsonObject();
            clientInfo.addProperty("name", "STSAgent");
            clientInfo.addProperty("version", "1.0.0");
            params.add("clientInfo", clientInfo);

            params.add("capabilities", new JsonObject());

            JsonObject response = sendRequest("initialize", params);
            if (response != null && response.has("result")) {
                initialized = true;
                sendNotification("notifications/initialized", new JsonObject());
                logger.info("MCP client initialized");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Failed to initialize MCP client", e);
            return false;
        }
    }

    /**
     * Execute game actions via MCP.
     * This is the main method for controlling the game.
     *
     * @param actions Array of action objects
     * @return Result of the execution
     */
    public ToolResult executeActions(JsonArray actions) {
        JsonObject args = new JsonObject();
        args.add("actions", actions);
        return callTool(EXECUTE_ACTIONS, args);
    }

    /**
     * Call an MCP tool.
     */
    public ToolResult callTool(String toolName, JsonObject arguments) {
        if (!initialized && !initialize()) {
            return ToolResult.error("MCP client not initialized");
        }

        try {
            JsonObject params = new JsonObject();
            params.addProperty("name", toolName);
            params.add("arguments", arguments != null ? arguments : new JsonObject());

            JsonObject response = sendRequest("tools/call", params);

            if (response == null) {
                return ToolResult.error("No response from MCP server");
            }

            if (response.has("error")) {
                JsonObject error = response.getAsJsonObject("error");
                String message = error.has("message") ? error.get("message").getAsString() : "Unknown error";
                return ToolResult.error(message);
            }

            if (response.has("result")) {
                JsonObject result = response.getAsJsonObject("result");
                // Extract content from MCP tool result format
                if (result.has("content") && result.get("content").isJsonArray()) {
                    JsonArray content = result.getAsJsonArray("content");
                    if (content.size() > 0) {
                        JsonObject firstContent = content.get(0).getAsJsonObject();
                        if (firstContent.has("text")) {
                            String text = firstContent.get("text").getAsString();
                            boolean isError = result.has("isError") && result.get("isError").getAsBoolean();
                            return new ToolResult(!isError, text);
                        }
                    }
                }
                return new ToolResult(true, result.toString());
            }

            return ToolResult.error("Invalid response format");
        } catch (Exception e) {
            logger.error("Error calling tool: " + toolName, e);
            return ToolResult.error(e.getMessage());
        }
    }

    /**
     * Get the execute_actions tool definition in OpenAI format.
     */
    public JsonObject getExecuteActionsTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", EXECUTE_ACTIONS);
        function.addProperty("description",
                "Execute game actions. Available actions: " +
                "play_card(card_index OR card_name, target_index for attacks), " +
                "end_turn, " +
                "choose(choice_index - 1-based), " +
                "proceed, skip, cancel, confirm, " +
                "use_potion(potion_slot, target_index?), " +
                "discard_potion(potion_slot), " +
                "select_cards(drop:[cards] OR keep:[cards]). " +
                "Example: [{\"action\":\"play_card\",\"card_name\":\"Bash\",\"target_index\":1},{\"action\":\"end_turn\"}]"
        );

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject actionsParam = new JsonObject();
        actionsParam.addProperty("type", "array");
        actionsParam.addProperty("description",
                "Array of action objects. Each has 'action' field plus parameters. " +
                "Indices are 1-based and stable (don't recalculate as actions execute)."
        );
        properties.add("actions", actionsParam);
        parameters.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("actions");
        parameters.add("required", required);

        function.add("parameters", parameters);
        tool.add("function", function);

        return tool;
    }

    /**
     * Update base URL.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = normalizeUrl(baseUrl);
        this.initialized = false;
        this.sessionId = null;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ========== Internal Methods ==========

    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private JsonObject sendRequest(String method, JsonObject params) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", requestId.getAndIncrement());
        request.addProperty("method", method);
        request.add("params", params);

        String requestBody = gson.toJson(request);
        logger.debug("MCP request: {}", requestBody);

        Request.Builder httpRequestBuilder = new Request.Builder()
                .url(baseUrl + "/mcp")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(requestBody, JSON));

        if (sessionId != null) {
            httpRequestBuilder.addHeader("Mcp-Session-Id", sessionId);
        }

        try (Response response = client.newCall(httpRequestBuilder.build()).execute()) {
            String newSessionId = response.header("Mcp-Session-Id");
            if (newSessionId != null) {
                sessionId = newSessionId;
            }

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("MCP request failed: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "{}";
            logger.debug("MCP response: {}", responseBody);

            return new JsonParser().parse(responseBody).getAsJsonObject();
        }
    }

    private void sendNotification(String method, JsonObject params) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("jsonrpc", "2.0");
            request.addProperty("method", method);
            request.add("params", params);

            Request.Builder httpRequestBuilder = new Request.Builder()
                    .url(baseUrl + "/mcp")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(request), JSON));

            if (sessionId != null) {
                httpRequestBuilder.addHeader("Mcp-Session-Id", sessionId);
            }

            client.newCall(httpRequestBuilder.build()).execute().close();
        } catch (Exception e) {
            logger.warn("Failed to send notification: {}", method, e);
        }
    }

    /**
     * Result of a tool call.
     */
    public static class ToolResult {
        public final boolean success;
        public final String message;

        public ToolResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ToolResult error(String message) {
            return new ToolResult(false, message);
        }

        public static ToolResult success(String message) {
            return new ToolResult(true, message);
        }

        @Override
        public String toString() {
            return success ? message : "Error: " + message;
        }
    }
}
