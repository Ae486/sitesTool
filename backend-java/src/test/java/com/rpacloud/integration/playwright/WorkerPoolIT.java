package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.execution.worker.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

/**
 * Integration test: real subprocess lifecycle with Playwright browsers.
 * Verifies: start → ready → ping/pong → execute DSL → result → shutdown.
 * Covers: Chromium, Edge, multi-type SubPool isolation, per-context proxy.
 */
class WorkerPoolIT {

    private static HttpServer httpServer;
    private static String baseUrl;
    // Lightweight forward proxy: tracks requests, forwards to the real httpServer
    private static HttpServer proxyServer;
    private static String proxyUrl;
    private static final AtomicInteger proxyHitCount = new AtomicInteger();

    @BeforeAll
    static void startServers() throws IOException {
        // Origin server
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            byte[] body = """
                    <!DOCTYPE html>
                    <html><head><title>Worker Test</title></head>
                    <body><h1 id="title">Worker Pool OK</h1></body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        httpServer.start();
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        // Minimal HTTP proxy — handles GET by forwarding, counts hits
        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/", exchange -> {
            proxyHitCount.incrementAndGet();
            // Proxy receives the full URL; forward to origin
            try (Socket sock = new Socket("127.0.0.1", httpServer.getAddress().getPort())) {
                String path = exchange.getRequestURI().getPath();
                if (path == null || path.isEmpty()) path = "/";
                OutputStream out = sock.getOutputStream();
                out.write(("GET " + path + " HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n").getBytes());
                out.flush();
                InputStream in = sock.getInputStream();
                // Skip HTTP headers
                StringBuilder headers = new StringBuilder();
                int prev = 0, cur;
                while ((cur = in.read()) != -1) {
                    headers.append((char) cur);
                    if (prev == '\n' && cur == '\n') break;
                    if (cur != '\r') prev = cur;
                }
                byte[] body = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("X-Proxied", "true");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.getResponseBody().close();
        });
        proxyServer.start();
        proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort();
    }

    @AfterAll
    static void stopServers() {
        if (httpServer != null) httpServer.stop(0);
        if (proxyServer != null) proxyServer.stop(0);
    }

    @BeforeEach
    void resetProxyCounter() {
        proxyHitCount.set(0);
    }

    @Test
    @Timeout(60)
    void workerSubprocess_readyPingPongShutdown() {
        var factory = new SubprocessWorkerFactory(null);
        var key = new WorkerKey("chromium", true, null);
        WorkerHandle worker = factory.create(key);

        try {
            assertThat(worker.isProcessAlive()).isTrue();

            // ping/pong
            worker.sendLine(WorkerProtocol.serializePing());
            String pongLine = worker.readLine(5000);
            assertThat(pongLine).isNotNull();
            Map<String, Object> pong = WorkerProtocol.deserialize(pongLine);
            assertThat(WorkerProtocol.getType(pong)).isEqualTo("pong");
        } finally {
            worker.destroy();
        }

        // Process should exit after shutdown
        try { worker.getProcess().waitFor(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}
        assertThat(worker.getProcess().isAlive()).isFalse();
    }

    @Test
    @Timeout(60)
    @SuppressWarnings("unchecked")
    void workerSubprocess_executeDsl_returnsResult() {
        var factory = new SubprocessWorkerFactory(null);
        var key = new WorkerKey("chromium", true, null);
        WorkerHandle worker = factory.create(key);

        try {
            // DSL: navigate to test page + extract title text
            String dsl = """
                    {"steps":[
                      {"type":"navigate","url":"%s"},
                      {"type":"extract","selector":"#title","variable":"title_text","attribute":"textContent"}
                    ]}""".formatted(baseUrl);

            var cmd = new WorkerProtocol.ExecuteCommand("test-exec-1", 1L, dsl, null, null);
            worker.sendLine(WorkerProtocol.serializeExecute(cmd));

            // Read result (longer timeout — first task may involve cold page load)
            String resultLine = worker.readLine(30_000);
            assertThat(resultLine).isNotNull();

            Map<String, Object> msg = WorkerProtocol.deserialize(resultLine);
            assertThat(WorkerProtocol.getType(msg)).isEqualTo("result");

            WorkerProtocol.TaskResult result = WorkerProtocol.toTaskResult(msg);
            assertThat(result.executionId()).isEqualTo("test-exec-1");
            assertThat(result.payload()).containsKey("status");
            assertThat(result.payload().get("status")).isEqualTo("success");
            assertThat(result.payload().get("steps_executed")).isEqualTo(2);
        } finally {
            worker.destroy();
        }
    }

    @Test
    @Timeout(60)
    void workerSubprocess_executeSecondTask_browserReused() {
        var factory = new SubprocessWorkerFactory(null);
        var key = new WorkerKey("chromium", true, null);
        WorkerHandle worker = factory.create(key);

        try {
            String dsl = """
                    {"steps":[{"type":"navigate","url":"%s"}]}""".formatted(baseUrl);

            // Task 1
            worker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("exec-1", 1L, dsl, null, null)));
            String r1 = worker.readLine(30_000);
            assertThat(r1).isNotNull();
            assertThat(WorkerProtocol.getType(WorkerProtocol.deserialize(r1))).isEqualTo("result");

            // Task 2 — same worker, browser reused (no cold start)
            worker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("exec-2", 2L, dsl, null, null)));
            String r2 = worker.readLine(15_000); // Should be faster (no browser launch)
            assertThat(r2).isNotNull();
            Map<String, Object> msg2 = WorkerProtocol.deserialize(r2);
            assertThat(WorkerProtocol.getType(msg2)).isEqualTo("result");

            WorkerProtocol.TaskResult result2 = WorkerProtocol.toTaskResult(msg2);
            assertThat(result2.executionId()).isEqualTo("exec-2");
            assertThat(result2.payload().get("status")).isEqualTo("success");

            // Worker still alive
            assertThat(worker.isProcessAlive()).isTrue();
        } finally {
            worker.destroy();
        }
    }

    @Test
    @Timeout(90)
    void fullPoolLifecycle_borrowExecuteReturn() {
        var config = new RpaProperties.WorkerPool();
        config.setGlobalMaxActive(2);
        config.setMaxActivePerKey(2);
        config.setMaxTasksPerWorker(10);
        config.setMaxLifetimeMinutes(60);
        config.setMaxIdleMinutes(10);
        config.setAcquireTimeoutMs(30_000);

        var factory = new SubprocessWorkerFactory(null);
        var pool = new WorkerPool(factory, config);
        var key = new WorkerKey("chromium", true, null);

        try {
            // Borrow worker from pool (cold start)
            WorkerHandle worker = pool.borrow(key, java.time.Duration.ofSeconds(30));
            assertThat(worker.isProcessAlive()).isTrue();
            assertThat(pool.activeCount(key)).isEqualTo(1);

            // Execute task
            String dsl = """
                    {"steps":[{"type":"navigate","url":"%s"}]}""".formatted(baseUrl);
            worker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("pool-exec-1", 1L, dsl, null, null)));
            String resultLine = worker.readLine(30_000);
            assertThat(resultLine).isNotNull();
            assertThat(WorkerProtocol.getType(WorkerProtocol.deserialize(resultLine))).isEqualTo("result");

            // Return to pool
            pool.returnWorker(worker);
            assertThat(pool.idleCount(key)).isEqualTo(1);
            assertThat(pool.activeCount(key)).isZero();

            // Borrow again — should get same worker (no cold start)
            WorkerHandle worker2 = pool.borrow(key, java.time.Duration.ofSeconds(5));
            assertThat(worker2.getId()).isEqualTo(worker.getId());
            assertThat(worker2.isProcessAlive()).isTrue();

            pool.returnWorker(worker2);
        } finally {
            pool.shutdown();
        }
    }

    // ---- Edge browser ----

    @Test
    @Timeout(60)
    void edgeWorker_executeDsl_returnsResult() {
        var factory = new SubprocessWorkerFactory(null);
        var key = new WorkerKey("edge", true, null);
        WorkerHandle worker = factory.create(key);

        try {
            assertThat(worker.isProcessAlive()).isTrue();

            String dsl = """
                    {"steps":[
                      {"type":"navigate","url":"%s"},
                      {"type":"extract","selector":"#title","variable":"t","attribute":"textContent"}
                    ]}""".formatted(baseUrl);
            worker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("edge-exec-1", 1L, dsl, null, null)));

            String resultLine = worker.readLine(30_000);
            assertThat(resultLine).isNotNull();
            Map<String, Object> msg = WorkerProtocol.deserialize(resultLine);
            assertThat(WorkerProtocol.getType(msg)).isEqualTo("result");
            assertThat(WorkerProtocol.toTaskResult(msg).payload().get("status")).isEqualTo("success");
        } finally {
            worker.destroy();
        }
    }

    // ---- Multi-type SubPool isolation ----

    @Test
    @Timeout(90)
    void multiTypeSubPool_chromiumAndEdge_isolatedPools() {
        var config = new RpaProperties.WorkerPool();
        config.setGlobalMaxActive(4);
        config.setMaxActivePerKey(2);
        config.setMaxTasksPerWorker(10);
        config.setMaxLifetimeMinutes(60);
        config.setMaxIdleMinutes(10);
        config.setAcquireTimeoutMs(30_000);

        var factory = new SubprocessWorkerFactory(null);
        var pool = new WorkerPool(factory, config);
        var chromiumKey = new WorkerKey("chromium", true, null);
        var edgeKey = new WorkerKey("edge", true, null);

        try {
            // Borrow one from each type
            WorkerHandle chromiumWorker = pool.borrow(chromiumKey, Duration.ofSeconds(30));
            WorkerHandle edgeWorker = pool.borrow(edgeKey, Duration.ofSeconds(30));

            assertThat(pool.activeCount(chromiumKey)).isEqualTo(1);
            assertThat(pool.activeCount(edgeKey)).isEqualTo(1);
            assertThat(chromiumWorker.getKey()).isEqualTo(chromiumKey);
            assertThat(edgeWorker.getKey()).isEqualTo(edgeKey);

            // Execute on both
            String dsl = """
                    {"steps":[{"type":"navigate","url":"%s"}]}""".formatted(baseUrl);
            chromiumWorker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("ch-1", 1L, dsl, null, null)));
            edgeWorker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("ed-1", 2L, dsl, null, null)));

            String r1 = chromiumWorker.readLine(30_000);
            String r2 = edgeWorker.readLine(30_000);
            assertThat(r1).isNotNull();
            assertThat(r2).isNotNull();
            assertThat(WorkerProtocol.toTaskResult(WorkerProtocol.deserialize(r1)).executionId()).isEqualTo("ch-1");
            assertThat(WorkerProtocol.toTaskResult(WorkerProtocol.deserialize(r2)).executionId()).isEqualTo("ed-1");

            // Return both — each goes back to its own SubPool
            pool.returnWorker(chromiumWorker);
            pool.returnWorker(edgeWorker);
            assertThat(pool.idleCount(chromiumKey)).isEqualTo(1);
            assertThat(pool.idleCount(edgeKey)).isEqualTo(1);

            // Re-borrow: each key gets its own worker back
            WorkerHandle ch2 = pool.borrow(chromiumKey, Duration.ofSeconds(5));
            WorkerHandle ed2 = pool.borrow(edgeKey, Duration.ofSeconds(5));
            assertThat(ch2.getId()).isEqualTo(chromiumWorker.getId());
            assertThat(ed2.getId()).isEqualTo(edgeWorker.getId());

            pool.returnWorker(ch2);
            pool.returnWorker(ed2);
        } finally {
            pool.shutdown();
        }
    }

    // ---- Per-context proxy ----

    @Test
    @Timeout(60)
    void perContextProxy_sameWorker_proxiedAndDirect() {
        var factory = new SubprocessWorkerFactory(null);
        var key = new WorkerKey("chromium", true, null);
        WorkerHandle worker = factory.create(key);

        try {
            // Task 1: with proxy — request goes through proxyServer
            String dsl = """
                    {"steps":[{"type":"navigate","url":"%s"}]}""".formatted(baseUrl);
            worker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("proxy-1", 1L, dsl, proxyUrl, null)));
            String r1 = worker.readLine(30_000);
            assertThat(r1).isNotNull();
            assertThat(WorkerProtocol.toTaskResult(WorkerProtocol.deserialize(r1)).payload().get("status"))
                    .isEqualTo("success");
            int hitsAfterProxy = proxyHitCount.get();
            assertThat(hitsAfterProxy).isGreaterThan(0);

            // Task 2: no proxy — same worker, request goes direct
            int hitsBefore = proxyHitCount.get();
            worker.sendLine(WorkerProtocol.serializeExecute(
                    new WorkerProtocol.ExecuteCommand("direct-2", 2L, dsl, null, null)));
            String r2 = worker.readLine(15_000);
            assertThat(r2).isNotNull();
            assertThat(WorkerProtocol.toTaskResult(WorkerProtocol.deserialize(r2)).payload().get("status"))
                    .isEqualTo("success");
            // Proxy hit count should NOT increase for the direct task
            assertThat(proxyHitCount.get()).isEqualTo(hitsBefore);

            // Worker still alive — browser reused across proxy/no-proxy tasks
            assertThat(worker.isProcessAlive()).isTrue();
        } finally {
            worker.destroy();
        }
    }
}
