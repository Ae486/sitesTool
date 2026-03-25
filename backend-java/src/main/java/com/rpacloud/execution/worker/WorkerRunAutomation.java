package com.rpacloud.execution.worker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.rpacloud.execution.engine.DslParser;
import com.rpacloud.execution.engine.ParsedStep;
import com.rpacloud.execution.engine.PlaywrightExecutor;
import com.rpacloud.execution.engine.RunAutomation;

/**
 * Long-lived subprocess entry point for Worker pool mode.
 * <p>
 * Unlike {@link RunAutomation} (single-shot), this class:
 * - Creates Playwright + Browser ONCE at startup
 * - Reads NDJSON commands from stdin in a while-loop
 * - For each "execute" command: creates BrowserContext → runs DSL → closes Context → writes result
 * - Browser stays alive across tasks, only Context is created/destroyed per task
 * <p>
 * Usage: java -cp &lt;classpath&gt; WorkerRunAutomation
 *   &lt;browserType&gt; &lt;headless|headed&gt; [--browser-path path] [--internal-api-url url]
 */
public class WorkerRunAutomation {

    // NDJSON protocol output — the original stdout, kept separate from logging
    private static PrintStream protocolOut;

    public static void main(String[] args) {
        // Reserve original stdout for NDJSON protocol; redirect System.out to stderr
        // so logback/SLF4J/Playwright logs don't pollute the protocol channel.
        protocolOut = System.out;
        System.setOut(System.err);

        Args parsed = parseArgs(args);

        try (Playwright pw = Playwright.create()) {
            System.err.println("[Worker] Playwright created");
            Browser browser = launchBrowser(pw, parsed);
            System.err.println("[Worker] Browser launched");
            writeLine(WorkerProtocol.serializeReady());
            System.err.println("[Worker] Ready sent");

            BufferedReader stdin = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = stdin.readLine()) != null) {
                Map<String, Object> msg = WorkerProtocol.deserialize(line);
                String type = WorkerProtocol.getType(msg);

                switch (type) {
                    case WorkerProtocol.TYPE_EXECUTE -> handleExecute(msg, browser, parsed);
                    case WorkerProtocol.TYPE_PING -> writeLine(WorkerProtocol.serializePong());
                    case WorkerProtocol.TYPE_SHUTDOWN -> {
                        System.err.println("[Worker] Shutdown received, exiting");
                        browser.close();
                        return;
                    }
                    default -> System.err.println("[Worker] Unknown message type: " + type);
                }
            }
            // stdin EOF — parent process died
            System.err.println("[Worker] stdin EOF, exiting");
            browser.close();
        } catch (Exception e) {
            System.err.println("[Worker] Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleExecute(Map<String, Object> msg, Browser browser, Args parsed) {
        WorkerProtocol.ExecuteCommand cmd = WorkerProtocol.toExecuteCommand(msg);

        try {
            DslParser parser = new DslParser();
            List<ParsedStep> steps = parser.parse(cmd.dslJson());

            // Per-context proxy: each task can use a different proxy
            var contextOptions = new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setIgnoreHTTPSErrors(true);
            if (cmd.proxyUrl() != null && !cmd.proxyUrl().isBlank()) {
                contextOptions.setProxy(new com.microsoft.playwright.options.Proxy(cmd.proxyUrl()));
            }

            BrowserContext context = browser.newContext(contextOptions);
            context.addInitScript(ANTI_DETECT_SCRIPT);
            Page page = context.newPage();

            try {
                // Create executor per task (lightweight: just handler objects)
                // internalToken is per-task because it encodes executionId
                PlaywrightExecutor executor = new PlaywrightExecutor(
                        parsed.headless, parsed.browserType, parsed.browserPath,
                        Path.of("data/screenshots"), null,
                        parsed.internalApiUrl, cmd.internalToken());

                PlaywrightExecutor.ExecutionResult result =
                        executor.executeOnPage(page, cmd.flowId(), steps);

                Map<String, Object> output = RunAutomation.buildOutputMap(result, cmd.executionId());
                writeLine(WorkerProtocol.serializeResult(cmd.executionId(), output));
            } finally {
                context.close();
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            writeLine(WorkerProtocol.serializeError(cmd.executionId(), errorMsg));
            System.err.println("[Worker] Execute error for " + cmd.executionId() + ": " + errorMsg);
        }
    }

    private static Browser launchBrowser(Playwright pw, Args parsed) {
        BrowserType launcher = switch (parsed.browserType) {
            case "firefox" -> pw.firefox();
            default -> pw.chromium();
        };
        var launchOptions = new BrowserType.LaunchOptions().setHeadless(parsed.headless);
        if ("chrome".equals(parsed.browserType)) launchOptions.setChannel("chrome");
        else if ("edge".equals(parsed.browserType)) launchOptions.setChannel("msedge");
        else if ("custom".equals(parsed.browserType) && parsed.browserPath != null)
            launchOptions.setExecutablePath(Path.of(parsed.browserPath));
        // No browser-level proxy — Playwright 1.47+ supports per-context proxy
        // on Windows Chromium without placeholder (PR #31724 fixed upstream bug).

        System.err.println("[Worker] Launching " + parsed.browserType
                + " (headless=" + parsed.headless + ")");
        return launcher.launch(launchOptions);
    }

    static Args parseArgs(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: <browserType> <headless|headed> [--browser-path path] [--internal-api-url url]");
        }
        String browserType = args[0];
        boolean headless = "headless".equals(args[1]);
        String browserPath = null;
        String internalApiUrl = null;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--browser-path" -> { if (i + 1 < args.length) browserPath = args[++i]; }
                case "--internal-api-url" -> { if (i + 1 < args.length) internalApiUrl = args[++i]; }
            }
        }
        return new Args(browserType, headless, browserPath, internalApiUrl);
    }

    record Args(String browserType, boolean headless, String browserPath, String internalApiUrl) {}

    /** Write NDJSON line to the protocol channel (original stdout, not redirected). */
    private static void writeLine(String json) {
        protocolOut.println(json);
        protocolOut.flush();
    }

    private static final String ANTI_DETECT_SCRIPT = """
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
            Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
            Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
            window.chrome = { runtime: {} };
            """;
}
