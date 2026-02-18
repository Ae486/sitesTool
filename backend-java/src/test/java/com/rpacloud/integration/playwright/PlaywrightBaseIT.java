package com.rpacloud.integration.playwright;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class PlaywrightBaseIT {

    protected static final Playwright playwright;
    protected static final Browser browser;
    protected static final HttpServer httpServer;
    protected static final String baseUrl;
    protected static final Path screenshotDir;

    static {
        try {
            // HTTP server serving test pages + API endpoint
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path)) path = "/test-page.html";

                // Dynamic API endpoint
                if ("/api/data".equals(path)) {
                    byte[] body = "{\"items\":[\"a\",\"b\",\"c\"],\"total\":3}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.getResponseBody().close();
                    return;
                }

                // Static resources from classpath
                String resourcePath = "playwright" + path;
                InputStream is = PlaywrightBaseIT.class.getClassLoader().getResourceAsStream(resourcePath);
                if (is == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                byte[] body = is.readAllBytes();
                String ct = path.endsWith(".html") ? "text/html; charset=utf-8" : "application/octet-stream";
                exchange.getResponseHeaders().set("Content-Type", ct);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
            });
            httpServer.setExecutor(null);
            httpServer.start();
            baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

            // Playwright browser
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

            // Screenshot temp dir
            screenshotDir = Files.createTempDirectory("pw-test-screenshots");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                browser.close();
                playwright.close();
                httpServer.stop(0);
            }));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Playwright test infrastructure", e);
        }
    }

    protected BrowserContext context;
    protected Page page;
    protected Map<String, Object> variables;

    @BeforeEach
    void setUp() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
        page = context.newPage();
        variables = new HashMap<>();
        page.navigate(baseUrl + "/test-page.html");
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    protected static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
