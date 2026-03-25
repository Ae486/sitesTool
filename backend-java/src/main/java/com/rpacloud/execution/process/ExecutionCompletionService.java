package com.rpacloud.execution.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.execution.engine.ErrorType;
import com.rpacloud.execution.worker.WorkerHandle;
import com.rpacloud.execution.worker.WorkerPool;
import com.rpacloud.execution.worker.WorkerProtocol;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.history.entity.CheckinHistory;
import com.rpacloud.history.repository.HistoryRepository;
import com.rpacloud.proxy.service.ProxyLeasePool;
import com.rpacloud.proxy.service.ProxyUseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Separate bean so Spring AOP can intercept @Async properly.
 * Self-invocation within the same class bypasses the proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionCompletionService {

    private final ProcessManager processManager;
    private final FlowRepository flowRepository;
    private final HistoryRepository historyRepository;
    private final RpaProperties rpaProperties;
    private final ObjectMapper objectMapper;
    private final Optional<ProxyLeasePool> proxyLeasePool;
    private final Optional<WorkerPool> workerPool;

    @Async("automationTaskExecutor")
    public void waitForCompletion(long flowId, Process process, String executionId, boolean leaseFromPool) {
        Instant startedAt = Instant.now();
        FlowStatus finalStatus = FlowStatus.failed;
        List<String> finalErrorTypes = List.of();
        try {
            java.util.concurrent.CompletableFuture<String> stdoutFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            java.util.concurrent.CompletableFuture<String> stderrFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(
                    rpaProperties.getExecution().getProcessTimeoutSeconds(),
                    java.util.concurrent.TimeUnit.SECONDS);

            Instant finishedAt = Instant.now();
            long durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();

            String stdout = stdoutFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                finalErrorTypes = List.of(ErrorType.PROCESS_TIMEOUT.value());
                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        stdout, makePayload(executionId, "failed", "Process timeout"),
                        "Process timeout", finalErrorTypes);
                return;
            }

            if (processManager.wasStopRequested(flowId)) {
                finalErrorTypes = List.of(ErrorType.MANUAL_STOP.value());
                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        stdout, makePayload(executionId, "failed", "Manual stop"),
                        "Manual stop", finalErrorTypes);
                return;
            }

            if (process.exitValue() == 0) {
                Map<String, Object> result = ProcessOutputParser.extractJson(stdout);
                if (result == null) {
                    finalErrorTypes = List.of(ErrorType.UNKNOWN.value());
                    saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                            stdout, makePayload(executionId, "failed", "No JSON payload in stdout"),
                            "No JSON payload in stdout", finalErrorTypes);
                    return;
                }
                result.putIfAbsent("execution_id", executionId);
                String status = String.valueOf(result.getOrDefault("status", "failed"));
                FlowStatus flowStatus = "success".equals(status) ? FlowStatus.success : FlowStatus.failed;

                List<String> errorTypes = extractErrorTypes(result);
                finalStatus = flowStatus;
                finalErrorTypes = errorTypes;
                String errorMessage = flowStatus == FlowStatus.failed ? buildErrorMessage(result) : null;
                List<String> screenshots = extractScreenshots(result);

                saveHistory(flowId, flowStatus, startedAt, finishedAt, durationMs,
                        stdout, objectMapper.writeValueAsString(result),
                        errorMessage, errorTypes, screenshots);
            } else {
                Map<String, Object> errPayload = ProcessOutputParser.extractJson(stderr);
                if (errPayload == null) errPayload = ProcessOutputParser.extractJson(stdout);
                String errorMessage = errPayload != null
                        ? String.valueOf(errPayload.getOrDefault("message", "Execution failed"))
                        : (stderr != null && !stderr.isBlank() ? stderr : "Execution failed");
                String payload = errPayload != null
                        ? objectMapper.writeValueAsString(errPayload)
                        : makePayload(executionId, "failed", errorMessage);
                finalErrorTypes = List.of(ErrorType.classify(errorMessage).value());

                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        stdout != null ? stdout : stderr, payload, errorMessage, finalErrorTypes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Wait interrupted for flow {}", flowId);
        } catch (Exception e) {
            log.error("Error waiting for flow {}: {}", flowId, e.getMessage(), e);
            try {
                saveHistory(flowId, FlowStatus.failed, startedAt, Instant.now(),
                        java.time.Duration.between(startedAt, Instant.now()).toMillis(),
                        null, makePayload(executionId, "failed", e.getMessage()),
                        e.getMessage(), List.of(ErrorType.classify(e).value()));
            } catch (Exception dbErr) {
                log.error("Failed to save error history for flow {}: {}", flowId, dbErr.getMessage());
            }
        } finally {
            if (leaseFromPool) {
                long elapsed = java.time.Duration.between(startedAt, Instant.now()).toMillis();
                final FlowStatus fs = finalStatus;
                final List<String> et = finalErrorTypes;
                proxyLeasePool.ifPresent(pool -> pool.returnProxy(executionId,
                        new ProxyUseResult(fs == FlowStatus.success, (int) elapsed, et)));
            }
            processManager.clearStopRequest(flowId);
            processManager.unregister(flowId);
            try {
                flowRepository.findById(flowId).ifPresent(f -> {
                    if (f.getLastStatus() == FlowStatus.running) {
                        f.setLastStatus(FlowStatus.idle);
                        flowRepository.save(f);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to reset flow {} status: {}", flowId, e.getMessage());
            }
        }
    }

    /**
     * Pooled path: send execute command to a borrowed Worker, read NDJSON result, save history.
     * Worker is returned to pool in finally block regardless of outcome.
     */
    @Async("automationTaskExecutor")
    public void waitForPooledCompletion(long flowId, WorkerHandle worker,
                                         String dslJson, String executionId,
                                         String proxyUrl, String internalToken,
                                         boolean leaseFromPool) {
        Instant startedAt = Instant.now();
        FlowStatus finalStatus = FlowStatus.failed;
        List<String> finalErrorTypes = List.of();
        try {
            var cmd = new WorkerProtocol.ExecuteCommand(executionId, flowId, dslJson, proxyUrl, internalToken);
            worker.sendLine(WorkerProtocol.serializeExecute(cmd));

            long timeoutMs = rpaProperties.getExecution().getProcessTimeoutSeconds() * 1000L;
            String resultLine = worker.readLine(timeoutMs);
            Instant finishedAt = Instant.now();
            long durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();

            if (processManager.wasStopRequested(flowId)) {
                finalErrorTypes = List.of(ErrorType.MANUAL_STOP.value());
                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        null, makePayload(executionId, "failed", "Manual stop"),
                        "Manual stop", finalErrorTypes);
                return;
            }

            if (resultLine == null) {
                finalErrorTypes = List.of(ErrorType.PROCESS_TIMEOUT.value());
                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        null, makePayload(executionId, "failed", "Worker timeout"),
                        "Worker timeout (no response within " + timeoutMs + "ms)", finalErrorTypes);
                return;
            }

            Map<String, Object> msg = WorkerProtocol.deserialize(resultLine);
            String type = WorkerProtocol.getType(msg);

            if (WorkerProtocol.TYPE_RESULT.equals(type)) {
                WorkerProtocol.TaskResult taskResult = WorkerProtocol.toTaskResult(msg);
                Map<String, Object> payload = taskResult.payload();
                payload.putIfAbsent("execution_id", executionId);
                String status = String.valueOf(payload.getOrDefault("status", "failed"));
                FlowStatus flowStatus = "success".equals(status) ? FlowStatus.success : FlowStatus.failed;

                List<String> errorTypes = extractErrorTypes(payload);
                finalStatus = flowStatus;
                finalErrorTypes = errorTypes;
                String errorMessage = flowStatus == FlowStatus.failed ? buildErrorMessage(payload) : null;
                List<String> screenshots = extractScreenshots(payload);

                saveHistory(flowId, flowStatus, startedAt, finishedAt, durationMs,
                        resultLine, objectMapper.writeValueAsString(payload),
                        errorMessage, errorTypes, screenshots);
            } else if (WorkerProtocol.TYPE_ERROR.equals(type)) {
                WorkerProtocol.TaskError taskError = WorkerProtocol.toTaskError(msg);
                finalErrorTypes = List.of(ErrorType.classify(taskError.message()).value());
                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        resultLine, makePayload(executionId, "failed", taskError.message()),
                        taskError.message(), finalErrorTypes);
            } else {
                finalErrorTypes = List.of(ErrorType.UNKNOWN.value());
                saveHistory(flowId, FlowStatus.failed, startedAt, finishedAt, durationMs,
                        resultLine, makePayload(executionId, "failed", "Unexpected message type: " + type),
                        "Unexpected worker message type: " + type, finalErrorTypes);
            }
        } catch (Exception e) {
            log.error("Error in pooled completion for flow {}: {}", flowId, e.getMessage(), e);
            try {
                saveHistory(flowId, FlowStatus.failed, startedAt, Instant.now(),
                        java.time.Duration.between(startedAt, Instant.now()).toMillis(),
                        null, makePayload(executionId, "failed", e.getMessage()),
                        e.getMessage(), List.of(ErrorType.classify(e).value()));
            } catch (Exception dbErr) {
                log.error("Failed to save error history for flow {}: {}", flowId, dbErr.getMessage());
            }
        } finally {
            if (leaseFromPool) {
                long elapsed = java.time.Duration.between(startedAt, Instant.now()).toMillis();
                final FlowStatus fs = finalStatus;
                final List<String> et = finalErrorTypes;
                proxyLeasePool.ifPresent(pool -> pool.returnProxy(executionId,
                        new ProxyUseResult(fs == FlowStatus.success, (int) elapsed, et)));
            }
            workerPool.ifPresent(pool -> pool.returnWorker(worker));
            processManager.clearStopRequest(flowId);
            processManager.unregisterWorker(flowId);
            try {
                flowRepository.findById(flowId).ifPresent(f -> {
                    if (f.getLastStatus() == FlowStatus.running) {
                        f.setLastStatus(FlowStatus.idle);
                        flowRepository.save(f);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to reset flow {} status: {}", flowId, e.getMessage());
            }
        }
    }

    private void saveHistory(long flowId, FlowStatus status, Instant startedAt, Instant finishedAt,
                              long durationMs, String logText, String resultPayload,
                              String errorMessage, List<String> errorTypes) {
        saveHistory(flowId, status, startedAt, finishedAt, durationMs, logText, resultPayload,
                errorMessage, errorTypes, List.of());
    }

    private void saveHistory(long flowId, FlowStatus status, Instant startedAt, Instant finishedAt,
                              long durationMs, String logText, String resultPayload,
                              String errorMessage, List<String> errorTypes, List<String> screenshots) {
        AutomationFlow flowRef = flowRepository.getReferenceById(flowId);
        CheckinHistory h = CheckinHistory.builder()
                .flow(flowRef)
                .status(status)
                .startedAt(toLocalDateTime(startedAt))
                .finishedAt(toLocalDateTime(finishedAt))
                .durationMs((int) durationMs)
                .log(logText)
                .resultPayload(resultPayload)
                .errorMessage(errorMessage)
                .errorTypes(errorTypes != null ? errorTypes : List.of())
                .screenshotFiles(screenshots != null ? screenshots : List.of())
                .build();
        historyRepository.save(h);
        log.info("Saved history for flow {} with status {}", flowId, status);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractErrorTypes(Map<String, Object> result) {
        List<String> types = new ArrayList<>();
        Object stepResults = result.get("step_results");
        if (stepResults instanceof List<?> steps) {
            for (Object item : steps) {
                if (!(item instanceof Map<?, ?> step)) continue;
                if (Boolean.TRUE.equals(step.get("success"))) continue;
                String error = step.get("error") instanceof String s ? s : null;
                if (error != null && error.startsWith("[") && error.contains("]")) {
                    types.add(error.substring(1, error.indexOf(']')));
                }
            }
        }
        return types.isEmpty() ? List.of(ErrorType.UNKNOWN.value()) : types;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractScreenshots(Map<String, Object> result) {
        List<String> paths = new ArrayList<>();
        Object stepResults = result.get("step_results");
        if (stepResults instanceof List<?> steps) {
            for (Object item : steps) {
                if (!(item instanceof Map<?, ?> step)) continue;
                if (step.get("screenshot_path") instanceof String p && !p.isBlank()) {
                    paths.add(java.nio.file.Path.of(p).getFileName().toString());
                }
            }
        }
        return paths;
    }

    private String buildErrorMessage(Map<String, Object> result) {
        Object stepResults = result.get("step_results");
        if (stepResults instanceof List<?> steps) {
            for (Object item : steps) {
                if (!(item instanceof Map<?, ?> step)) continue;
                if (Boolean.TRUE.equals(step.get("success"))) continue;
                return step.get("error") instanceof String s ? s : "Unknown error";
            }
        }
        return String.valueOf(result.getOrDefault("message", "Execution failed"));
    }

    private String makePayload(String executionId, String status, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "execution_id", executionId, "status", status, "message", message));
        } catch (Exception e) {
            return "{\"status\":\"failed\"}";
        }
    }

    private static java.time.LocalDateTime toLocalDateTime(Instant instant) {
        return java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
    }

    private static String readStream(java.io.InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return null;
        }
    }
}
