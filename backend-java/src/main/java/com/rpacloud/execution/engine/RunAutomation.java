package com.rpacloud.execution.engine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Subprocess entry point for automation execution.
 * Mirrors Python run_automation.py.
 *
 * Usage: java -cp <classpath> com.rpacloud.execution.engine.RunAutomation
 *        <flow_id> <dsl_json> [--headless|--headed] [--browser chromium]
 *        [--execution-id xxx] [--browser-path path]
 */
public class RunAutomation {

    public static void main(String[] args) {
        try {
            Args parsed = parseArgs(args);
            DslParser parser = new DslParser();
            List<ParsedStep> steps = parser.parse(parsed.dslJson);

            PlaywrightExecutor executor = new PlaywrightExecutor(
                    parsed.headless, parsed.browser, parsed.browserPath,
                    Path.of("data/screenshots"), parsed.proxyUrl,
                    parsed.internalApiUrl, parsed.internalToken,
                    parsed.useCdpMode, parsed.cdpPort, parsed.cdpUserDataDir);

            PlaywrightExecutor.ExecutionResult result = executor.execute(parsed.flowId, steps);

            Map<String, Object> output = buildOutputMap(result, parsed.executionId);
            System.out.println(new ObjectMapper().writeValueAsString(output));

        } catch (Exception e) {
            try {
                Map<String, Object> error = Map.of(
                        "status", "failed",
                        "execution_id", args.length > 0 ? extractExecutionId(args) : "",
                        "message", e.getMessage() != null ? e.getMessage() : e.toString()
                );
                System.err.println(new ObjectMapper().writeValueAsString(error));
            } catch (Exception ignored) {
                System.err.println("{\"status\":\"failed\",\"message\":\"" + e.getMessage() + "\"}");
            }
            System.exit(1);
        }
    }

    /** Build output map from execution result. Public for WorkerRunAutomation reuse. */
    public static Map<String, Object> buildOutputMap(PlaywrightExecutor.ExecutionResult result, String executionId) {
        List<Map<String, Object>> stepResults = new ArrayList<>();
        for (var sr : result.getStepResults()) {
            Map<String, Object> m = new HashMap<>();
            m.put("step_index", sr.getStepIndex());
            m.put("step_type", sr.getStepType());
            m.put("success", sr.isSuccess());
            m.put("duration_ms", sr.getDurationMs());
            if (sr.getMessage() != null) m.put("message", sr.getMessage());
            if (sr.getError() != null) m.put("error", sr.getError());
            if (sr.getExtractedData() != null) m.put("extracted_data", sr.getExtractedData());
            if (sr.getScreenshotPath() != null) m.put("screenshot_path", sr.getScreenshotPath());
            if (sr.getDescription() != null) m.put("description", sr.getDescription());
            stepResults.add(m);
        }
        Map<String, Object> output = new HashMap<>();
        output.put("execution_id", executionId);
        output.put("status", result.getStatus());
        output.put("steps_executed", result.getStepsExecuted());
        output.put("steps_failed", result.getStepsFailed());
        output.put("total_duration_ms", result.getTotalDurationMs());
        output.put("message", "Executed " + result.getStepsExecuted() + " steps, " + result.getStepsFailed() + " failed");
        output.put("step_results", stepResults);
        if (result.getTotalTokensUsed() > 0) {
            output.put("total_tokens_used", result.getTotalTokensUsed());
        }
        return output;
    }

    static Args parseArgs(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: <flow_id> <dsl_json|--dsl-stdin> [options]");
        long flowId = Long.parseLong(args[0]);
        String dslJson = args[1];
        boolean headless = true;
        String browser = "chromium";
        String browserPath = null;
        String executionId = UUID.randomUUID().toString().replace("-", "");
        String proxyUrl = null;
        String internalApiUrl = null;
        String internalToken = null;
        boolean readStdin = false;
        boolean useCdpMode = false;
        int cdpPort = 9222;
        String cdpUserDataDir = null;

        // Check if args[1] is --dsl-stdin flag
        if ("--dsl-stdin".equals(dslJson)) {
            readStdin = true;
            dslJson = null;
        }

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--headed" -> headless = false;
                case "--headless" -> headless = true;
                case "--browser" -> { if (i + 1 < args.length) browser = args[++i]; }
                case "--browser-path" -> { if (i + 1 < args.length) browserPath = args[++i]; }
                case "--execution-id" -> { if (i + 1 < args.length) executionId = args[++i]; }
                case "--proxy" -> { if (i + 1 < args.length) proxyUrl = args[++i]; }
                case "--internal-api-url" -> { if (i + 1 < args.length) internalApiUrl = args[++i]; }
                case "--internal-token" -> { if (i + 1 < args.length) internalToken = args[++i]; }
                case "--use-cdp-mode" -> useCdpMode = true;
                case "--cdp-port" -> { if (i + 1 < args.length) cdpPort = Integer.parseInt(args[++i]); }
                case "--cdp-user-data-dir" -> { if (i + 1 < args.length) cdpUserDataDir = args[++i]; }
                case "--dsl-stdin" -> readStdin = true;
            }
        }

        if (readStdin) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                dslJson = reader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                throw new RuntimeException("Failed to read DSL from stdin: " + e.getMessage(), e);
            }
        }

        if (dslJson == null || dslJson.isBlank()) {
            throw new IllegalArgumentException("DSL JSON is empty");
        }

        return new Args(flowId, dslJson, headless, browser, browserPath, executionId, proxyUrl, internalApiUrl, internalToken,
                useCdpMode, cdpPort, cdpUserDataDir);
    }

    private static String extractExecutionId(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--execution-id".equals(args[i])) return args[i + 1];
        }
        return "";
    }

    record Args(long flowId, String dslJson, boolean headless, String browser, String browserPath,
                String executionId, String proxyUrl, String internalApiUrl, String internalToken,
                boolean useCdpMode, int cdpPort, String cdpUserDataDir) {}
}
