package com.rpacloud.execution.process;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessManager {

    private final ConcurrentHashMap<Long, ProcessInfo> processes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> stopRequests = new ConcurrentHashMap<>();

    public record ProcessInfo(long flowId, Process process, Instant startedAt) {
        boolean isAlive() {
            return process.isAlive();
        }
    }

    public void register(long flowId, Process process) {
        processes.compute(flowId, (id, existing) -> {
            if (existing != null && existing.isAlive()) {
                throw new FlowAlreadyRunningException(flowId);
            }
            return new ProcessInfo(flowId, process, Instant.now());
        });
        stopRequests.remove(flowId);
        log.info("Registered process for flow {}, PID: {}", flowId, process.pid());
    }

    public void unregister(long flowId) {
        processes.remove(flowId);
    }

    public boolean isRunning(long flowId) {
        ProcessInfo info = processes.get(flowId);
        if (info == null) return false;
        if (!info.isAlive()) {
            processes.remove(flowId);
            return false;
        }
        return true;
    }

    public List<Long> getRunningFlowIds() {
        processes.entrySet().removeIf(e -> !e.getValue().isAlive());
        return List.copyOf(processes.keySet());
    }

    public boolean forceStop(long flowId) {
        ProcessInfo info = processes.get(flowId);
        if (info == null) return false;
        stopRequests.put(flowId, true);
        info.process().destroy();
        try {
            if (!info.process().waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                info.process().destroyForcibly();
                info.process().waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                log.warn("Force killed process for flow {}", flowId);
            } else {
                log.info("Gracefully terminated process for flow {}", flowId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            info.process().destroyForcibly();
        }
        processes.remove(flowId);
        return true;
    }

    public boolean wasStopRequested(long flowId) {
        return stopRequests.getOrDefault(flowId, false);
    }

    public void clearStopRequest(long flowId) {
        stopRequests.remove(flowId);
    }

    public void cleanup() {
        processes.entrySet().removeIf(e -> !e.getValue().isAlive());
    }
}
