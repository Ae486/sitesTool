package com.rpacloud.execution.engine.handlers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.StepType;
import com.rpacloud.execution.engine.VariableResolver;

public class HttpRequestHandler implements StepHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.HTTP_REQUEST};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) throws Exception {
        return handle(params, variables);
    }

    @SuppressWarnings("unchecked")
    public HandlerResult handle(Map<String, Object> params, Map<String, Object> variables) throws Exception {
        String url = VariableResolver.resolve((String) params.get("url"), variables);
        SsrfValidator.validate(url);
        return doRequest(url, params, variables);
    }

    /** Package-private: skips SSRF for unit tests against loopback server. */
    HandlerResult handleWithoutSsrf(Map<String, Object> params, Map<String, Object> variables) throws Exception {
        String url = VariableResolver.resolve((String) params.get("url"), variables);
        return doRequest(url, params, variables);
    }

    private HandlerResult doRequest(String url, Map<String, Object> params, Map<String, Object> variables) throws Exception {
        String method = ((String) params.getOrDefault("method", "GET")).toUpperCase();
        int timeout = params.get("timeout") instanceof Number n ? n.intValue() : 30000;
        String saveTo = params.get("save_to") instanceof String s ? s : null;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeout));

        // Headers
        Object headersRaw = params.get("headers");
        if (headersRaw instanceof Map<?, ?> hm) {
            for (Map.Entry<?, ?> entry : hm.entrySet()) {
                String val = VariableResolver.resolve(String.valueOf(entry.getValue()), variables);
                builder.header(String.valueOf(entry.getKey()), val);
            }
        }

        // Body
        String bodyStr = null;
        Object bodyRaw = params.get("body");
        if (bodyRaw != null) {
            if (bodyRaw instanceof Map || bodyRaw instanceof java.util.List) {
                // Resolve variables in nested structure
                String json = MAPPER.writeValueAsString(bodyRaw);
                bodyStr = VariableResolver.resolve(json, variables);
            } else {
                bodyStr = VariableResolver.resolve(String.valueOf(bodyRaw), variables);
            }
        }

        // Method + body
        switch (method) {
            case "POST" -> builder.POST(bodyPublisher(bodyStr, builder));
            case "PUT" -> builder.PUT(bodyPublisher(bodyStr, builder));
            case "DELETE" -> {
                if (bodyStr != null) builder.method("DELETE", HttpRequest.BodyPublishers.ofString(bodyStr));
                else builder.DELETE();
            }
            case "PATCH" -> builder.method("PATCH", bodyPublisher(bodyStr, builder));
            default -> builder.GET();
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        Map<String, Object> data = new HashMap<>();
        if (saveTo != null) {
            data.put(saveTo, response.body());
            data.put(saveTo + "_status", response.statusCode());
        }

        return HandlerResult.withData(
                method + " " + url + " → " + response.statusCode(),
                data);
    }

    private HttpRequest.BodyPublisher bodyPublisher(String body, HttpRequest.Builder builder) {
        if (body == null) return HttpRequest.BodyPublishers.noBody();
        builder.header("Content-Type", "application/json");
        return HttpRequest.BodyPublishers.ofString(body);
    }
}
