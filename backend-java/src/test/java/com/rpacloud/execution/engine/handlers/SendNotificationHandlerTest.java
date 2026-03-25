package com.rpacloud.execution.engine.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SendNotificationHandlerTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/internal/notification", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String resp = "{\"status\":\"sent\"}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsNotification() throws Exception {
        SendNotificationHandler handler = new SendNotificationHandler(
                "http://127.0.0.1:" + port, "test-token");

        Map<String, Object> params = new HashMap<>();
        params.put("message", "Hello ${name}");
        params.put("channel", "websocket");

        Map<String, Object> variables = new HashMap<>(Map.of("name", "World"));
        HandlerResult result = handler.handle(params, variables);

        assertThat(result.getMessage()).contains("Notification sent");
        assertThat(result.getMessage()).contains("Hello World");
    }

    @Test
    void skipsWhenNotConfigured() throws Exception {
        SendNotificationHandler handler = new SendNotificationHandler(null, null);

        HandlerResult result = handler.handle(Map.of("message", "test"), Map.of());
        assertThat(result.getMessage()).contains("skipped");
    }
}
