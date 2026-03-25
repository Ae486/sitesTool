package com.rpacloud.execution.process;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.rpacloud.execution.worker.WorkerHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessManager {

    private final ConcurrentHashMap<Long, ProcessInfo> processes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WorkerHandle> workerHandles = new ConcurrentHashMap<>();
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

    // ---- Worker handle tracking (pooled path) ----

    public void registerWorker(long flowId, WorkerHandle worker) {
        if (processes.containsKey(flowId) || workerHandles.containsKey(flowId)) {
            throw new FlowAlreadyRunningException(flowId);
        }
        workerHandles.put(flowId, worker);
        stopRequests.remove(flowId);
        log.info("Registered worker {} for flow {}", worker.getId(), flowId);
    }

    public void unregisterWorker(long flowId) {
        workerHandles.remove(flowId);
    }

    public boolean isRunning(long flowId) {
        // Check subprocess path
        ProcessInfo info = processes.get(flowId);
        if (info != null) {
            if (!info.isAlive()) { processes.remove(flowId); }
            else { return true; }
        }
        // Check worker path
        WorkerHandle wh = workerHandles.get(flowId);
        if (wh != null) {
            if (wh.isDestroyed()) { workerHandles.remove(flowId); }
            else { return true; }
        }
        return false;
    }

    public List<Long> getRunningFlowIds() {
        processes.entrySet().removeIf(e -> !e.getValue().isAlive());
        workerHandles.entrySet().removeIf(e -> e.getValue().isDestroyed());
        var ids = new java.util.HashSet<>(processes.keySet());
        ids.addAll(workerHandles.keySet());
        return List.copyOf(ids);
    }

    public boolean forceStop(long flowId) {
        // Try worker path first
        WorkerHandle wh = workerHandles.remove(flowId);
        if (wh != null) {
            stopRequests.put(flowId, true);
            wh.destroy();
            log.info("Destroyed worker {} for flow {}", wh.getId(), flowId);
            return true;
        }
        // Subprocess path
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
