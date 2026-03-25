package com.rpacloud.execution.engine.handlers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.VariableResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

@Slf4j
public class LlmAgentHandler {

    private static final int DEFAULT_MAX_STEPS = 15;
    private static final int MAX_STEPS_UPPER_BOUND = 100;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final String internalApiUrl;
    private final String internalToken;
    private final Path screenshotDir;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmAgentHandler(String internalApiUrl, String internalToken, Path screenshotDir) {
        this.internalApiUrl = internalApiUrl;
        this.internalToken = internalToken;
        this.screenshotDir = screenshotDir;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.objectMapper = new ObjectMapper();
    }

    public HandlerResult handle(Page page, Map<String, Object> params, Map<String, Object> variables)
            throws Exception {
        try {
            if (page == null) {
                throw new IllegalArgumentException("LlmAgentHandler requires a non-null Playwright page");
            }

            Map<String, Object> safeParams = params != null ? params : Map.of();
            Map<String, Object> safeVariables = variables != null ? variables : new LinkedHashMap<>();

            String goal = asString(safeParams.get("goal"));
            goal = VariableResolver.resolve(goal, safeVariables);
            if (goal == null || goal.isBlank()) {
                throw new IllegalArgumentException("LLM agent requires a non-empty 'goal' parameter");
            }

            int maxSteps = resolveMaxSteps(safeParams.get("max_steps"));
            String requestedModel = asNonBlankString(safeParams.get("model"));
            String saveTo = asNonBlankString(safeParams.get("save_to"));

            validateInternalConfig();
            LlmCredential credential = fetchCredential();
            String effectiveModel = requestedModel != null ? requestedModel : credential.model();
            String apiBaseUrl = stripTrailingV1(credential.baseUrl());

            OpenAiApi api = OpenAiApi.builder()
                    .apiKey(credential.apiKey())
                    .baseUrl(apiBaseUrl)
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder().model(effectiveModel).build())
                    .build();

            BrowserTools browserTools = new BrowserTools(page, screenshotDir);
            ToolCallback[] callbacks = ToolCallbacks.from(browserTools);

            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .model(effectiveModel)
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(false)
                    .build();

            ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
            Prompt currentPrompt = new Prompt(goal, options);

            int totalTokensUsed = 0;
            int iteration = 0;
            boolean returnDirect = false;
            String returnDirectMessage = null;
            ChatResponse response = null;

            while (iteration < maxSteps) {
                iteration++;
                response = chatModel.call(currentPrompt);
                int stepTokens = extractTokens(response);
                totalTokensUsed += stepTokens;

                boolean hasToolCalls = response != null && response.hasToolCalls();
                log.info(
                        "LLM agent iteration {}/{}, hasToolCalls={}, stepTokens={}, totalTokensUsed={}",
                        iteration,
                        maxSteps,
                        hasToolCalls,
                        stepTokens,
                        totalTokensUsed);

                if (!hasToolCalls) {
                    break;
                }

                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(currentPrompt, response);
                if (toolExecutionResult.returnDirect()) {
                    returnDirect = true;
                    returnDirectMessage = extractReturnDirectMessage(toolExecutionResult);
                    break;
                }

                currentPrompt = new Prompt(toolExecutionResult.conversationHistory(), options);
            }

            if (!returnDirect && response != null && response.hasToolCalls() && iteration >= maxSteps) {
                throw new IllegalStateException(
                        "LLM agent exceeded max_steps=" + maxSteps + " before producing a final assistant message");
            }

            String finalMessage = returnDirect ? returnDirectMessage : extractAssistantMessage(response);
            if (finalMessage == null || finalMessage.isBlank()) {
                finalMessage = "LLM agent completed with empty response";
            }

            Map<String, Object> extractedData = new LinkedHashMap<>();
            if (saveTo != null) {
                String variableName = normalizeVariableName(saveTo);
                safeVariables.put(variableName, finalMessage);
                extractedData.put(variableName, finalMessage);
            }

            log.info(
                    "LLM agent completed in {} iteration(s), model={}, totalTokensUsed={}",
                    iteration,
                    effectiveModel,
                    totalTokensUsed);

            return HandlerResult.builder()
                    .message(finalMessage)
                    .extractedData(extractedData)
                    .totalTokensUsed(totalTokensUsed)
                    .build();
        }
        catch (Exception e) {
            log.error("LlmAgentHandler failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void validateInternalConfig() {
        if (internalApiUrl == null || internalApiUrl.isBlank() || internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("LLM agent requires --internal-api-url and --internal-token");
        }
    }

    private LlmCredential fetchCredential() throws Exception {
        String endpoint = normalizeBaseUrl(internalApiUrl) + "/internal/llm/credential";
        String payload = objectMapper.writeValueAsString(Map.of("internal_token", internalToken));

        log.info("Fetching internal LLM credential from {}", endpoint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(READ_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Failed to fetch LLM credential (HTTP "
                            + response.statusCode()
                            + "): "
                            + truncate(response.body(), 500));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String apiKey = requiredField(root, "api_key", "apiKey");
        String baseUrl = requiredField(root, "base_url", "baseUrl");
        String model = requiredField(root, "model", null);

        return new LlmCredential(apiKey, baseUrl, model);
    }

    private static String requiredField(JsonNode node, String primary, String fallback) {
        String value = node.path(primary).asText(null);
        if ((value == null || value.isBlank()) && fallback != null) {
            value = node.path(fallback).asText(null);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required credential field: " + primary);
        }
        return value;
    }

    private static int resolveMaxSteps(Object raw) {
        if (raw == null) {
            return DEFAULT_MAX_STEPS;
        }
        int resolved = DEFAULT_MAX_STEPS;
        if (raw instanceof Number number) {
            int value = number.intValue();
            resolved = value > 0 ? value : DEFAULT_MAX_STEPS;
        } else if (raw instanceof String text) {
            try {
                int value = Integer.parseInt(text.trim());
                resolved = value > 0 ? value : DEFAULT_MAX_STEPS;
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return Math.min(resolved, MAX_STEPS_UPPER_BOUND);
    }

    private static int extractTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return Math.max(usage.getTotalTokens(), 0);
    }

    private static String extractAssistantMessage(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    private static String extractReturnDirectMessage(ToolExecutionResult result) {
        List<Message> history = result != null ? result.conversationHistory() : List.of();
        if (history.isEmpty()) {
            return "";
        }

        Message lastMessage = history.get(history.size() - 1);
        if (lastMessage instanceof ToolResponseMessage toolResponseMessage) {
            List<String> responses = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                if (response.responseData() != null && !response.responseData().isBlank()) {
                    responses.add(response.responseData());
                }
            }
            return String.join("\n", responses);
        }

        return lastMessage.getText() != null ? lastMessage.getText() : "";
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Spring AI appends /v1/chat/completions automatically — strip trailing /v1 to avoid duplication. */
    private static String stripTrailingV1(String url) {
        if (url == null) return url;
        String trimmed = url.trim();
        if (trimmed.endsWith("/v1")) return trimmed.substring(0, trimmed.length() - 3);
        if (trimmed.endsWith("/v1/")) return trimmed.substring(0, trimmed.length() - 4);
        return trimmed;
    }

    private static String normalizeVariableName(String name) {
        return name.startsWith("$") ? name.substring(1) : name;
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String asNonBlankString(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private record LlmCredential(String apiKey, String baseUrl, String model) {
    }
}
