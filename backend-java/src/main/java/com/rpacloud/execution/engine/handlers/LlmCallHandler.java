package com.rpacloud.execution.engine.handlers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LlmCallHandler {

    private final String internalApiUrl;
    private final String internalToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmCallHandler(String internalApiUrl, String internalToken) {
        this.internalApiUrl = internalApiUrl;
        this.internalToken = internalToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public HandlerResult handle(Map<String, Object> params, Map<String, Object> variables) throws Exception {
        String prompt = resolveVariables((String) params.get("prompt"), variables);
        String model = params.get("model") instanceof String m ? m : null;
        String saveTo = (String) params.get("save_to");

        if (prompt == null || prompt.isBlank()) {
            return HandlerResult.of("LLM call skipped: empty prompt");
        }
        if (internalApiUrl == null || internalToken == null) {
            throw new RuntimeException("LLM call requires --internal-api-url and --internal-token");
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("prompt", prompt);
        body.put("internal_token", internalToken);
        if (model != null && !model.isBlank()) body.put("model", model);
        if (params.get("max_tokens") instanceof Number n) body.put("max_tokens", n.intValue());
        if (params.get("temperature") instanceof Number n) body.put("temperature", n.doubleValue());

        String jsonBody = objectMapper.writeValueAsString(body);
        String url = internalApiUrl + "/internal/llm/chat";

        log.info("LLM call: url={}, prompt length={}", url, prompt.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> httpResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (httpResp.statusCode() != 200) {
            throw new RuntimeException("LLM call failed (HTTP " + httpResp.statusCode() + "): " + httpResp.body());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> respMap = objectMapper.readValue(httpResp.body(), Map.class);
        String response = (String) respMap.get("response");
        Object tokensUsed = respMap.get("tokens_used");

        if (saveTo != null && !saveTo.isBlank()) {
            String varName = saveTo.startsWith("$") ? saveTo.substring(1) : saveTo;
            variables.put(varName, response);
        }

        return HandlerResult.of("LLM response: " + tokensUsed + " tokens");
    }

    private String resolveVariables(String text, Map<String, Object> variables) {
        if (text == null) return null;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            text = text.replace("$" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        return text;
    }
}
