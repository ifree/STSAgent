package stsagent.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OpenAI-compatible LLM API client.
 * Supports OpenAI, OpenRouter, and other compatible services.
 * Includes tool/function calling support.
 */
public class LLMClient {
    private static final Logger logger = LogManager.getLogger(LLMClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;

    private String apiKey;
    private String baseUrl;
    private String model;

    public LLMClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.gson = new Gson();
    }

    /**
     * Represents a tool call from the LLM.
     */
    public static class ToolCall {
        public final String id;
        public final String name;
        public final JsonObject arguments;

        public ToolCall(String id, String name, JsonObject arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * Represents a chat response that may contain tool calls.
     */
    public static class ChatResponse {
        public final String content;
        public final List<ToolCall> toolCalls;
        public final String finishReason;

        public ChatResponse(String content, List<ToolCall> toolCalls, String finishReason) {
            this.content = content;
            this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
            this.finishReason = finishReason;
        }

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }

    /**
     * Synchronous chat completion.
     */
    public String chat(List<LLMMessage> messages) throws IOException {
        JsonObject requestBody = buildRequestBody(messages, false);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("LLM API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject json = new JsonParser().parse(responseBody).getAsJsonObject();

            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }

    /**
     * Asynchronous chat completion.
     */
    public CompletableFuture<String> chatAsync(List<LLMMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chat(messages);
            } catch (IOException e) {
                throw new RuntimeException("LLM API call failed", e);
            }
        });
    }

    /**
     * Streaming chat completion with callback for each chunk.
     */
    public void chatStream(List<LLMMessage> messages, Consumer<String> onChunk, Runnable onComplete) {
        JsonObject requestBody = buildRequestBody(messages, true);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("Stream request failed", e);
                onComplete.run();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    logger.error("Stream response error: {}", response.code());
                    onComplete.run();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            try {
                                JsonObject chunk = new JsonParser().parse(data).getAsJsonObject();
                                JsonArray choices = chunk.getAsJsonArray("choices");
                                if (choices != null && choices.size() > 0) {
                                    JsonObject delta = choices.get(0).getAsJsonObject()
                                            .getAsJsonObject("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").getAsString();
                                        onChunk.accept(content);
                                    }
                                }
                            } catch (Exception e) {
                                // Skip malformed chunks
                            }
                        }
                    }
                } finally {
                    onComplete.run();
                }
            }
        });
    }

    private JsonObject buildRequestBody(List<LLMMessage> messages, boolean stream) {
        return buildRequestBody(messages, stream, null);
    }

    private JsonObject buildRequestBody(List<LLMMessage> messages, boolean stream, JsonArray tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", stream);

        JsonArray messagesArray = new JsonArray();
        for (LLMMessage msg : messages) {
            messagesArray.add(msg.toJson());
        }
        body.add("messages", messagesArray);

        if (tools != null && tools.size() > 0) {
            body.add("tools", tools);
        }

        return body;
    }

    /**
     * Chat completion with tool calling support.
     * Returns a ChatResponse that may contain tool calls.
     */
    public ChatResponse chatWithTools(List<LLMMessage> messages, JsonArray tools) throws IOException {
        JsonObject requestBody = buildRequestBody(messages, false, tools);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("LLM API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject json = new JsonParser().parse(responseBody).getAsJsonObject();

            JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").getAsString() : null;

            // Extract content
            String content = null;
            if (message.has("content") && !message.get("content").isJsonNull()) {
                content = message.get("content").getAsString();
            }

            // Extract tool calls
            List<ToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                for (JsonElement elem : toolCallsArray) {
                    JsonObject tc = elem.getAsJsonObject();
                    String id = tc.get("id").getAsString();
                    JsonObject function = tc.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    String argsStr = function.get("arguments").getAsString();
                    JsonObject args = new JsonParser().parse(argsStr).getAsJsonObject();
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }

            return new ChatResponse(content, toolCalls, finishReason);
        }
    }

    /**
     * Async version of chatWithTools.
     */
    public CompletableFuture<ChatResponse> chatWithToolsAsync(List<LLMMessage> messages, JsonArray tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chatWithTools(messages, tools);
            } catch (IOException e) {
                throw new RuntimeException("LLM API call failed", e);
            }
        });
    }

    /**
     * Test connection to the LLM API.
     */
    public boolean testConnection() {
        try {
            String response = chat(Collections.singletonList(
                    LLMMessage.user("Say 'OK' if you can hear me.")
            ));
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            logger.error("LLM connection test failed", e);
            return false;
        }
    }

    // Configuration update methods
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
    public void setModel(String model) { this.model = model; }

    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
}
