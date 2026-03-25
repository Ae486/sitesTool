package com.rpacloud.execution.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * WorkerFactory implementation that spawns real JVM subprocesses
 * running {@link WorkerRunAutomation} as the main class.
 */
@Slf4j
public class SubprocessWorkerFactory implements WorkerFactory {

    private static final String WORKER_CLASS = "com.rpacloud.execution.worker.WorkerRunAutomation";
    private static final String PROPERTIES_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";

    private final String internalApiUrl;

    public SubprocessWorkerFactory(String internalApiUrl) {
        this.internalApiUrl = internalApiUrl;
    }

    @Override
    public WorkerHandle create(WorkerKey key) {
        List<String> cmd = buildCommand(key);
        log.info("Starting worker subprocess for key {}: {}", key, String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            WorkerHandle handle = new WorkerHandle(key, process);

            // Wait for "ready" message. Skip non-JSON lines (SLF4J/JVM noise on stdout).
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                String line = handle.readLine(Math.min(remaining, 5_000));
                if (line == null) {
                    if (!process.isAlive()) {
                        throw new RuntimeException("Worker process exited with code " + process.exitValue()
                                + " before sending ready, key=" + key);
                    }
                    continue;
                }
                try {
                    Map<String, Object> msg = WorkerProtocol.deserialize(line);
                    if (WorkerProtocol.TYPE_READY.equals(WorkerProtocol.getType(msg))) {
                        log.info("Worker {} started successfully for key {}, PID: {}",
                                handle.getId(), key, process.pid());
                        return handle;
                    }
                    log.debug("Worker startup: skipping non-ready message: {}", line);
                } catch (Exception e) {
                    log.debug("Worker startup: skipping non-JSON stdout line: {}", line);
                }
            }

            handle.destroy();
            // Capture stderr for diagnosis
            String stderr = "";
            try {
                var errStream = process.getErrorStream();
                if (errStream.available() > 0) {
                    stderr = new String(errStream.readNBytes(errStream.available()), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
            throw new RuntimeException("Worker subprocess did not send ready signal within 30s, key=" + key
                    + (stderr.isEmpty() ? "" : "\nstderr: " + stderr));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create worker for key " + key + ": " + e.getMessage(), e);
        }
    }

    List<String> buildCommand(WorkerKey key) {
        String javaPath = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);

        boolean fatJar = classpath.endsWith(".jar") && !classpath.contains(java.io.File.pathSeparator);
        if (fatJar) {
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("-Dloader.main=" + WORKER_CLASS);
            cmd.add(PROPERTIES_LAUNCHER);
        } else {
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add(WORKER_CLASS);
        }

        // Static args (per-worker, not per-task)
        cmd.add(key.browserType());
        cmd.add(key.headless() ? "headless" : "headed");

        if (key.userDataDir() != null && !key.userDataDir().isBlank()) {
            cmd.add("--browser-path");
            cmd.add(key.userDataDir());
        }

        if (internalApiUrl != null) {
            cmd.add("--internal-api-url");
            cmd.add(internalApiUrl);
        }

        return cmd;
    }
}
