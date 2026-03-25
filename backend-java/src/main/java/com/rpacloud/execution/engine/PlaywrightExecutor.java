package com.rpacloud.execution.engine;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.rpacloud.execution.engine.handlers.*;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes automation flows using Playwright sync API.
 * Runs inside a subprocess — no Spring context available.
 */
@Slf4j
public class PlaywrightExecutor {

    private final boolean headless;
    private final String browserType;
    private final String browserPath;
    private final Path screenshotDir;
    private final String proxyUrl;
    private final String internalApiUrl;
    private final String internalToken;
    private final boolean useCdpMode;
    private final int cdpPort;
    private final String cdpUserDataDir;

    // Handler instances (no Spring, manual wiring)
    private final NavigationHandler navigationHandler = new NavigationHandler();
    private final InteractionHandler interactionHandler = new InteractionHandler();
    private final WaitHandler waitHandler = new WaitHandler();
    private final ExtractHandler extractHandler;
    private final AssertionHandler assertionHandler = new AssertionHandler();
    private final MiscHandler miscHandler = new MiscHandler();
    private final NetworkHandler networkHandler = new NetworkHandler();
    private final LlmCallHandler llmCallHandler;
    private final HttpRequestHandler httpRequestHandler = new HttpRequestHandler();
    private final SendNotificationHandler sendNotificationHandler;
    private final LlmAgentHandler llmAgentHandler;

    public PlaywrightExecutor(boolean headless, String browserType, String browserPath, Path screenshotDir) {
        this(headless, browserType, browserPath, screenshotDir, null, null, null, false, 9222, null);
    }

    public PlaywrightExecutor(boolean headless, String browserType, String browserPath, Path screenshotDir, String proxyUrl) {
        this(headless, browserType, browserPath, screenshotDir, proxyUrl, null, null, false, 9222, null);
    }

    public PlaywrightExecutor(boolean headless, String browserType, String browserPath,
                              Path screenshotDir, String proxyUrl,
                              String internalApiUrl, String internalToken) {
        this(headless, browserType, browserPath, screenshotDir, proxyUrl, internalApiUrl, internalToken, false, 9222, null);
    }

    public PlaywrightExecutor(boolean headless, String browserType, String browserPath,
                              Path screenshotDir, String proxyUrl,
                              String internalApiUrl, String internalToken,
                              boolean useCdpMode, int cdpPort, String cdpUserDataDir) {
        this.headless = headless;
        this.browserType = browserType;
        this.browserPath = browserPath;
        this.screenshotDir = screenshotDir;
        this.proxyUrl = proxyUrl;
        this.internalApiUrl = internalApiUrl;
        this.internalToken = internalToken;
        this.useCdpMode = useCdpMode;
        this.cdpPort = cdpPort;
        this.cdpUserDataDir = cdpUserDataDir;
        this.extractHandler = new ExtractHandler(screenshotDir);
        this.llmCallHandler = (internalApiUrl != null && internalToken != null)
                ? new LlmCallHandler(internalApiUrl, internalToken) : null;
        this.sendNotificationHandler = (internalApiUrl != null && internalToken != null)
                ? new SendNotificationHandler(internalApiUrl, internalToken) : null;
        this.llmAgentHandler = (internalApiUrl != null && internalToken != null)
                ? new LlmAgentHandler(internalApiUrl, internalToken, screenshotDir) : null;
        screenshotDir.toFile().mkdirs();
    }

    public ExecutionResult execute(long flowId, List<ParsedStep> steps) {
        Instant startedAt = Instant.now();
        List<StepResult> stepResults = new ArrayList<>();
        Map<String, Object> variables = new HashMap<>();
        int stepsFailed = 0;
        long totalTokensUsed = 0;

        try (Playwright pw = Playwright.create()) {
            Browser browser;
            BrowserContext context;
            Page page;
            boolean isCdpConnection = false;

            if (useCdpMode) {
                // --- CDP mode: connect to externally-managed browser ---
                if (proxyUrl != null && !proxyUrl.isBlank()) {
                    log.warn("CDP mode: proxy setting '{}' will be ignored (CDP uses browser's own network)", proxyUrl);
                }
                CdpBrowserManager manager = new CdpBrowserManager();
                if (!CdpBrowserManager.isCdpReady(cdpPort)) {
                    boolean started = manager.startBrowser(browserType, cdpPort, browserPath, cdpUserDataDir, headless);
                    if (!started) {
                        throw new RuntimeException("CDP mode: failed to start browser on port " + cdpPort);
                    }
                }
                // Final readiness check with retry
                if (!CdpBrowserManager.isCdpReady(cdpPort)) {
                    log.warn("CDP not ready, waiting 5s...");
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    if (!CdpBrowserManager.isCdpReady(cdpPort)) {
                        throw new RuntimeException("CDP interface on port " + cdpPort + " is not responding");
                    }
                }
                String endpoint = "http://localhost:" + cdpPort;
                log.info("Connecting to CDP endpoint: {}", endpoint);
                browser = pw.chromium().connectOverCDP(endpoint,
                        new BrowserType.ConnectOverCDPOptions().setTimeout(60000));
                // Reuse existing context (preserves user logins) or create new
                context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
                page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                isCdpConnection = true;
                log.info("CDP connected: {} context(s), {} page(s)", browser.contexts().size(), context.pages().size());
            } else {
                // --- Normal mode: launch new browser ---
                BrowserType launcher = selectLauncher(pw);
                var launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
                if ("chrome".equals(browserType)) launchOptions.setChannel("chrome");
                else if ("edge".equals(browserType)) launchOptions.setChannel("msedge");
                else if ("custom".equals(browserType) && browserPath != null) launchOptions.setExecutablePath(Path.of(browserPath));
                // No browser-level proxy — Playwright 1.47+ supports per-context proxy
                // on Windows Chromium without placeholder (PR #31724 fixed upstream bug).
                browser = launcher.launch(launchOptions);
                var contextOptions = new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .setLocale("zh-CN")
                        .setTimezoneId("Asia/Shanghai")
                        .setIgnoreHTTPSErrors(true);
                if (proxyUrl != null && !proxyUrl.isBlank()) {
                    contextOptions.setProxy(new com.microsoft.playwright.options.Proxy(proxyUrl));
                }
                context = browser.newContext(contextOptions);
                context.addInitScript(ANTI_DETECT_SCRIPT);
                page = context.newPage();
                isCdpConnection = false;
            }

            try {
                for (int idx = 0; idx < steps.size(); idx++) {
                    ParsedStep step = steps.get(idx);
                    log.info("[flow={} step={}/{} type={}] Executing", flowId, idx + 1, steps.size(), step.getType().value());
                    Instant stepStart = Instant.now();
                    try {
                        HandlerResult result = dispatch(page, step, idx, variables, flowId);
                        long durationMs = Duration.between(stepStart, Instant.now()).toMillis();
                        totalTokensUsed += result.getTotalTokensUsed();
                        if (result.getExtractedData() != null && !result.getExtractedData().isEmpty()) {
                            variables.putAll(result.getExtractedData());
                        }
                        stepResults.add(StepResult.builder()
                                .stepIndex(idx).stepType(step.getType().value())
                                .success(true).durationMs(durationMs)
                                .message(result.getMessage())
                                .extractedData(result.getExtractedData())
                                .screenshotPath(result.getScreenshotPath())
                                .description(step.getDescription())
                                .build());
                    } catch (Exception e) {
                        long durationMs = Duration.between(stepStart, Instant.now()).toMillis();
                        stepsFailed++;
                        String errorScreenshot = captureErrorScreenshot(page, flowId, idx);
                        String errorType = ErrorType.classify(e).value();
                        String detail = formatErrorDetail(e, step, page);
                        stepResults.add(StepResult.builder()
                                .stepIndex(idx).stepType(step.getType().value())
                                .success(false).durationMs(durationMs)
                                .error("[" + errorType + "] " + detail)
                                .screenshotPath(errorScreenshot)
                                .description(step.getDescription())
                                .build());
                        log.warn("Step {} failed: {}", idx + 1, e.getMessage());
                    }
                }
            } finally {
                if (isCdpConnection) {
                    // CDP: disconnect only, browser process stays alive for next execution
                    try { browser.close(); } catch (Exception e) { log.debug("CDP disconnect: {}", e.getMessage()); }
                    log.info("CDP mode: disconnected (browser process kept running on port {})", cdpPort);
                } else {
                    context.close();
                    browser.close();
                }
            }
        }

        Instant completedAt = Instant.now();
        long totalMs = Duration.between(startedAt, completedAt).toMillis();
        String status = stepsFailed == 0 ? "success" : (stepsFailed < steps.size() ? "partial" : "failed");

        return new ExecutionResult(flowId, status, startedAt, completedAt, totalMs,
                steps.size(), stepsFailed, stepResults, variables, totalTokensUsed);
    }

    /**
     * Execute steps on an externally-provided Page (Worker pool mode).
     * Caller manages Browser/Context lifecycle; this method only runs the step dispatch loop.
     */
    public ExecutionResult executeOnPage(Page page, long flowId, List<ParsedStep> steps) {
        Instant startedAt = Instant.now();
        List<StepResult> stepResults = new ArrayList<>();
        Map<String, Object> variables = new HashMap<>();
        int stepsFailed = 0;
        long totalTokensUsed = 0;

        for (int idx = 0; idx < steps.size(); idx++) {
            ParsedStep step = steps.get(idx);
            log.info("[flow={} step={}/{} type={}] Executing", flowId, idx + 1, steps.size(), step.getType().value());
            Instant stepStart = Instant.now();
            try {
                HandlerResult result = dispatch(page, step, idx, variables, flowId);
                long durationMs = Duration.between(stepStart, Instant.now()).toMillis();
                totalTokensUsed += result.getTotalTokensUsed();
                if (result.getExtractedData() != null && !result.getExtractedData().isEmpty()) {
                    variables.putAll(result.getExtractedData());
                }
                stepResults.add(StepResult.builder()
                        .stepIndex(idx).stepType(step.getType().value())
                        .success(true).durationMs(durationMs)
                        .message(result.getMessage())
                        .extractedData(result.getExtractedData())
                        .screenshotPath(result.getScreenshotPath())
                        .description(step.getDescription())
                        .build());
            } catch (Exception e) {
                long durationMs = Duration.between(stepStart, Instant.now()).toMillis();
                stepsFailed++;
                String errorScreenshot = captureErrorScreenshot(page, flowId, idx);
                String errorType = ErrorType.classify(e).value();
                String detail = formatErrorDetail(e, step, page);
                stepResults.add(StepResult.builder()
                        .stepIndex(idx).stepType(step.getType().value())
                        .success(false).durationMs(durationMs)
                        .error("[" + errorType + "] " + detail)
                        .screenshotPath(errorScreenshot)
                        .description(step.getDescription())
                        .build());
                log.warn("Step {} failed: {}", idx + 1, e.getMessage());
            }
        }

        Instant completedAt = Instant.now();
        long totalMs = Duration.between(startedAt, completedAt).toMillis();
        String status = stepsFailed == 0 ? "success" : (stepsFailed < steps.size() ? "partial" : "failed");

        return new ExecutionResult(flowId, status, startedAt, completedAt, totalMs,
                steps.size(), stepsFailed, stepResults, variables, totalTokensUsed);
    }

    private HandlerResult dispatch(Page page, ParsedStep step, int idx,
                                    Map<String, Object> variables, long flowId) throws Exception {
        Map<String, Object> params = step.getParams();
        return switch (step.getType()) {
            case NAVIGATE -> navigationHandler.navigate(page, params, variables);
            case NEW_TAB -> navigationHandler.newTab(page, params, variables);
            case SWITCH_TAB -> navigationHandler.switchTab(page, params, variables);
            case CLOSE_TAB -> navigationHandler.closeTab(page, params, variables);
            case FRAME_SWITCH -> navigationHandler.frameSwitch(page, params, variables);
            case FRAME_MAIN -> navigationHandler.frameMain(page, params, variables);
            case CLICK -> interactionHandler.click(page, params, variables);
            case INPUT -> interactionHandler.input(page, params, variables);
            case SELECT -> interactionHandler.select(page, params, variables);
            case CHECKBOX -> interactionHandler.checkbox(page, params, variables);
            case SCROLL -> interactionHandler.scroll(page, params, variables);
            case HOVER -> interactionHandler.hover(page, params, variables);
            case KEYBOARD -> interactionHandler.keyboard(page, params, variables);
            case TRY_CLICK -> interactionHandler.tryClick(page, params, variables);
            case DRAG_DROP -> interactionHandler.dragDrop(page, params, variables);
            case UPLOAD_FILE -> interactionHandler.uploadFile(page, params, variables);
            case WAIT_FOR -> waitHandler.waitFor(page, params, variables);
            case WAIT_TIME -> waitHandler.waitTime(page, params, variables);
            case RANDOM_DELAY -> waitHandler.randomDelay(page, params, variables);
            case EXTRACT -> extractHandler.extract(page, params, variables);
            case EXTRACT_ALL -> extractHandler.extractAll(page, params, variables);
            case SCREENSHOT -> extractHandler.screenshot(page, params, variables, flowId, idx);
            case ASSERT_TEXT -> assertionHandler.assertText(page, params, variables);
            case ASSERT_VISIBLE -> assertionHandler.assertVisible(page, params, variables);
            case IF_EXISTS -> assertionHandler.ifExists(page, params, variables);
            case SET_VARIABLE -> miscHandler.setVariable(page, params, variables);
            case EVAL_JS -> miscHandler.evalJs(page, params, variables);
            case DIALOG_HANDLE -> miscHandler.dialogHandle(page, params, variables);
            case CAPTURE_NETWORK -> networkHandler.captureNetwork(page, params, variables);
            case WAIT_FOR_NETWORK -> networkHandler.waitForNetwork(page, params, variables);
            case LLM_CALL -> {
                if (llmCallHandler == null) throw new RuntimeException("LLM not configured: missing --internal-api-url / --internal-token");
                yield llmCallHandler.handle(params, variables);
            }
            case HTTP_REQUEST -> httpRequestHandler.handle(params, variables);
            case SEND_NOTIFICATION -> {
                if (sendNotificationHandler == null) throw new RuntimeException("Notification not configured: missing --internal-api-url / --internal-token");
                yield sendNotificationHandler.handle(params, variables);
            }
            case LLM_AGENT -> {
                if (llmAgentHandler == null) throw new RuntimeException("LLM agent not configured: missing --internal-api-url / --internal-token");
                yield llmAgentHandler.handle(page, params, variables);
            }
            case LOOP -> executeLoop(page, params, variables, flowId);
            case LOOP_ARRAY -> executeLoopArray(page, params, variables, flowId);
            case IF_ELSE -> executeIfElse(page, params, variables, flowId);
        };
    }

    // --- Control flow handlers (need self access for child step execution) ---

    @SuppressWarnings("unchecked")
    private HandlerResult executeLoop(Page page, Map<String, Object> params,
                                       Map<String, Object> variables, long flowId) throws Exception {
        int times = ((Number) params.getOrDefault("times", 1)).intValue();
        List<Map<String, Object>> children = (List<Map<String, Object>>) params.getOrDefault("children", List.of());
        if (children.isEmpty()) return HandlerResult.of("Loop: no children (" + times + " iterations)");
        for (int i = 0; i < times; i++) {
            log.info("Loop iteration {}/{}", i + 1, times);
            for (Map<String, Object> child : children) {
                executeChildStep(page, child, variables, flowId);
            }
        }
        return HandlerResult.of("Loop completed: " + times + " iterations, " + children.size() + " steps each");
    }

    @SuppressWarnings("unchecked")
    private HandlerResult executeLoopArray(Page page, Map<String, Object> params,
                                            Map<String, Object> variables, long flowId) throws Exception {
        String arrayVar = (String) params.get("array_variable");
        String itemVar = (String) params.get("item_variable");
        List<Map<String, Object>> children = (List<Map<String, Object>>) params.getOrDefault("children", List.of());
        Object raw = variables.getOrDefault(arrayVar, List.of());
        List<?> arr = raw instanceof List<?> list ? list : List.of();
        if (children.isEmpty()) return HandlerResult.of("Loop array: no children (" + arr.size() + " items)");
        for (int i = 0; i < arr.size(); i++) {
            variables.put(itemVar, arr.get(i));
            log.info("Loop array {}/{}: {}={}", i + 1, arr.size(), itemVar, arr.get(i));
            for (Map<String, Object> child : children) {
                executeChildStep(page, child, variables, flowId);
            }
        }
        return HandlerResult.of("Loop array completed: " + arr.size() + " iterations over " + arrayVar);
    }

    @SuppressWarnings("unchecked")
    private HandlerResult executeIfElse(Page page, Map<String, Object> params,
                                         Map<String, Object> variables, long flowId) throws Exception {
        String condType = params.getOrDefault("condition_type", "variable_truthy").toString();
        String condVar = params.getOrDefault("condition_variable", "").toString();
        String condSelector = params.getOrDefault("condition_selector", "").toString();
        String condValue = params.getOrDefault("condition_value", "").toString();
        double timeout = params.get("timeout") instanceof Number n ? n.doubleValue() : 3000;
        List<Map<String, Object>> children = (List<Map<String, Object>>) params.getOrDefault("children", List.of());
        List<Map<String, Object>> elseChildren = (List<Map<String, Object>>) params.getOrDefault("else_children", List.of());

        boolean isTrue = evaluateCondition(page, condType, condVar, condSelector, condValue, timeout, variables);
        String branch = isTrue ? "then" : "else";
        List<Map<String, Object>> target = isTrue ? children : elseChildren;

        if (target.isEmpty()) return HandlerResult.of("If-else: " + condType + " = " + isTrue + ", " + branch + " branch empty");
        for (Map<String, Object> child : target) {
            executeChildStep(page, child, variables, flowId);
        }
        return HandlerResult.of("If-else: " + condType + " = " + isTrue + ", executed " + branch + " (" + target.size() + " steps)");
    }

    private boolean evaluateCondition(Page page, String type, String variable, String selector,
                                       String value, double timeout, Map<String, Object> variables) {
        try {
            return switch (type) {
                case "variable_truthy" -> {
                    Object v = variables.get(variable);
                    yield v != null && !v.equals(false) && !v.equals(0) && !"".equals(v) && !"false".equals(v);
                }
                case "variable_equals" -> String.valueOf(variables.getOrDefault(variable, "")).equals(value);
                case "variable_contains" -> String.valueOf(variables.getOrDefault(variable, "")).contains(value);
                case "variable_greater" -> Double.parseDouble(String.valueOf(variables.getOrDefault(variable, "0"))) > Double.parseDouble(value);
                case "variable_less" -> Double.parseDouble(String.valueOf(variables.getOrDefault(variable, "0"))) < Double.parseDouble(value);
                case "element_exists" -> {
                    try { page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeout)); yield true; }
                    catch (Exception e) { yield false; }
                }
                case "element_visible" -> {
                    try {
                        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(timeout));
                        yield true;
                    } catch (Exception e) { yield false; }
                }
                case "element_text_equals" -> {
                    try {
                        var el = page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeout));
                        yield el != null && value.equals(el.textContent().strip());
                    } catch (Exception e) { yield false; }
                }
                case "element_text_contains" -> {
                    try {
                        var el = page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeout));
                        yield el != null && el.textContent().contains(value);
                    } catch (Exception e) { yield false; }
                }
                default -> false;
            };
        } catch (Exception e) {
            log.warn("Condition evaluation error: {}", e.getMessage());
            return false;
        }
    }

    private void executeChildStep(Page page, Map<String, Object> stepData,
                                   Map<String, Object> variables, long flowId) throws Exception {
        String typeStr = (String) stepData.get("type");
        StepType type = StepType.fromValue(typeStr);
        Map<String, Object> params = new HashMap<>(stepData);
        params.remove("type");
        params.remove("description");
        String desc = stepData.get("description") instanceof String s ? s : null;
        ParsedStep child = new ParsedStep(type, params, desc);
        dispatch(page, child, 0, variables, flowId);
    }

    private String captureErrorScreenshot(Page page, long flowId, int idx) {
        try {
            String filename = "error_flow_" + flowId + "_step_" + idx + "_" + System.currentTimeMillis() + ".png";
            Path path = screenshotDir.resolve(filename);
            page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            return path.toString();
        } catch (Exception e) {
            log.warn("Failed to capture error screenshot: {}", e.getMessage());
            return null;
        }
    }

    private String formatErrorDetail(Exception e, ParsedStep step, Page page) {
        StringBuilder sb = new StringBuilder(e.getMessage() != null ? e.getMessage() : e.toString());
        try { sb.append(" | URL: ").append(page.url()); } catch (Exception ignored) {}
        if (step.getParams().containsKey("selector")) {
            sb.append(" | Selector: ").append(step.getParams().get("selector"));
        }
        if (step.getDescription() != null) sb.append(" | Step: ").append(step.getDescription());
        return sb.toString();
    }

    private BrowserType selectLauncher(Playwright pw) {
        return switch (browserType) {
            case "firefox" -> pw.firefox();
            default -> pw.chromium();
        };
    }

    // --- Result types ---

    @Value
    public static class StepResult {
        int stepIndex;
        String stepType;
        boolean success;
        long durationMs;
        String message;
        Map<String, Object> extractedData;
        String screenshotPath;
        String error;
        String description;

        @Builder
        public StepResult(int stepIndex, String stepType, boolean success, long durationMs,
                          String message, Map<String, Object> extractedData, String screenshotPath,
                          String error, String description) {
            this.stepIndex = stepIndex;
            this.stepType = stepType;
            this.success = success;
            this.durationMs = durationMs;
            this.message = message;
            this.extractedData = extractedData;
            this.screenshotPath = screenshotPath;
            this.error = error;
            this.description = description;
        }
    }

    @Value
    public static class ExecutionResult {
        long flowId;
        String status;
        Instant startedAt;
        Instant completedAt;
        long totalDurationMs;
        int stepsExecuted;
        int stepsFailed;
        List<StepResult> stepResults;
        Map<String, Object> variables;
        long totalTokensUsed;
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
