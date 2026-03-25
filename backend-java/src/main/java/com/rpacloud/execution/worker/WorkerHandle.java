package com.rpacloud.execution.worker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Represents a single Worker (long-lived subprocess with Browser).
 * <p>
 * Phase 1 mode: metadata only (no Process), for pool mechanics testing.
 * Phase 2+ mode: holds real Process with NDJSON stdin/stdout IO.
 */
public class WorkerHandle {

    private final String id;
    private final WorkerKey key;
    private final Instant createdAt;
    private volatile int taskCount;
    private volatile long lastReturnAt;
    private volatile boolean healthy = true;
    private volatile boolean destroyed;

    // Phase 2: subprocess IO (null when created without Process)
    private final Process process;
    private final BufferedReader stdoutReader;
    private final PrintWriter stdinWriter;

    /** Phase 1 constructor: metadata only, no real subprocess. */
    public WorkerHandle(WorkerKey key) {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.key = key;
        this.createdAt = Instant.now();
        this.process = null;
        this.stdoutReader = null;
        this.stdinWriter = null;
    }

    /** Phase 2 constructor: wraps a real subprocess with NDJSON IO. */
    public WorkerHandle(WorkerKey key, Process process) {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.key = key;
        this.createdAt = Instant.now();
        this.process = process;
        this.stdoutReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stdinWriter = new PrintWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    // --- Getters ---

    public String getId() { return id; }
    public WorkerKey getKey() { return key; }
    public Instant getCreatedAt() { return createdAt; }
    public int getTaskCount() { return taskCount; }
    public long getLastReturnAt() { return lastReturnAt; }
    public Process getProcess() { return process; }

    public void incrementTaskCount() { taskCount++; }
    public void setLastReturnAt(long epochMs) { this.lastReturnAt = epochMs; }

    public boolean isHealthy() {
        return healthy && !destroyed && (process == null || process.isAlive());
    }

    public void setHealthy(boolean healthy) { this.healthy = healthy; }
    public boolean isDestroyed() { return destroyed; }

    /** Lifetime in minutes since creation. */
    public long ageMinutes() {
        return Duration.between(createdAt, Instant.now()).toMinutes();
    }

    /** Idle time in minutes since last return. Returns 0 if never returned. */
    public long idleMinutes() {
        if (lastReturnAt == 0) return 0;
        return Duration.ofMillis(System.currentTimeMillis() - lastReturnAt).toMinutes();
    }

    // --- NDJSON IO (Phase 2) ---

    /** Send a JSON line to the worker's stdin. */
    public void sendLine(String jsonLine) {
        if (stdinWriter == null) throw new IllegalStateException("No process attached");
        stdinWriter.println(jsonLine);
        stdinWriter.flush();
    }

    /**
     * Read a JSON line from the worker's stdout with timeout.
     * Uses polling (reader.ready()) to avoid thread-leak from CompletableFuture timeouts.
     */
    public String readLine(long timeoutMs) {
        if (stdoutReader == null) throw new IllegalStateException("No process attached");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (stdoutReader.ready()) {
                    return stdoutReader.readLine();
                }
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public boolean isProcessAlive() {
        return process != null && process.isAlive();
    }

    // --- Lifecycle ---

    /**
     * Destroy this worker. Sends shutdown command if possible, then kills process.
     */
    public void destroy() {
        if (destroyed) return;
        destroyed = true;

        // Try graceful shutdown via protocol
        if (stdinWriter != null) {
            try {
                stdinWriter.println(WorkerProtocol.serializeShutdown());
                stdinWriter.flush();
            } catch (Exception ignored) {}
        }

        // Kill process
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
