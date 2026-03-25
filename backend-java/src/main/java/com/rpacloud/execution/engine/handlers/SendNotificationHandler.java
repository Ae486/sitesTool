package com.rpacloud.execution.engine.handlers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.execution.engine.VariableResolver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendNotificationHandler {

    private final String internalApiUrl;
    private final String internalToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SendNotificationHandler(String internalApiUrl, String internalToken) {
        this.internalApiUrl = internalApiUrl;
        this.internalToken = internalToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public HandlerResult handle(Map<String, Object> params, Map<String, Object> variables) throws Exception {
        String message = VariableResolver.resolve((String) params.getOrDefault("message", ""), variables);
        String channel = (String) params.getOrDefault("channel", "websocket");

        if (internalApiUrl == null || internalToken == null) {
            log.warn("Notification skipped: no internal API configured");
            return HandlerResult.of("Notification skipped: not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("internal_token", internalToken);
        body.put("channel", channel);
        body.put("message", message);

        if ("email".equals(channel)) {
            String to = VariableResolver.resolve((String) params.getOrDefault("to", ""), variables);
            String subject = VariableResolver.resolve((String) params.getOrDefault("subject", "RPA Notification"), variables);
            body.put("to", to);
            body.put("subject", subject);
        }

        String jsonBody = objectMapper.writeValueAsString(body);
        String url = internalApiUrl + "/internal/notification";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            log.warn("Notification failed (HTTP {}): {}", resp.statusCode(), resp.body());
            return HandlerResult.of("Notification failed: HTTP " + resp.statusCode());
        }

        return HandlerResult.of("Notification sent via " + channel + ": " + message);
    }
}
