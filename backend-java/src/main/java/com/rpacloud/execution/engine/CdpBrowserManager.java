package com.rpacloud.execution.engine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages browser lifecycle for CDP mode.
 * Java port of Python backend/app/services/automation/browser_launcher.py.
 */
@Slf4j
public class CdpBrowserManager {

    private static final Set<String> SKIP_FILES = Set.of(
            "lockfile", "SingletonLock", "SingletonSocket", "SingletonCookie");

    private static final Map<String, List<String>> BROWSER_PATHS = Map.of(
            "chrome", List.of(
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"),
            "edge", List.of(
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"));

    private Process process;
    private int port;

    public static boolean isCdpReady(int port) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/json/version"))
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static String findBrowserExecutable(String browserType, String customPath) {
        if (customPath != null && !customPath.isBlank() && Files.exists(Path.of(customPath))) {
            return customPath;
        }
        // Search standard paths
        List<String> paths = new ArrayList<>(BROWSER_PATHS.getOrDefault(browserType, List.of()));
        // Add %LOCALAPPDATA% paths
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            if ("chrome".equals(browserType)) {
                paths.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
            } else if ("edge".equals(browserType)) {
                paths.add(localAppData + "\\Microsoft\\Edge\\Application\\msedge.exe");
            }
        }
        for (String p : paths) {
            if (Files.exists(Path.of(p))) return p;
        }
        return null;
    }

    public static Path getDefaultUserDataDir(String browserType) {
        Path home = Path.of(System.getProperty("user.home"));
        Path dir = switch (browserType) {
            case "chrome" -> home.resolve("AppData/Local/Google/Chrome/User Data");
            case "edge" -> home.resolve("AppData/Local/Microsoft/Edge/User Data");
            default -> null;
        };
        return (dir != null && Files.isDirectory(dir)) ? dir : null;
    }

    public boolean startBrowser(String browserType, int port, String customPath,
                                String userDataDir, boolean headless) {
        // Already running?
        if (isCdpReady(port)) {
            log.info("CDP already responding on port {}, reusing", port);
            this.port = port;
            return true;
        }

        String browserPath = findBrowserExecutable(browserType, customPath);
        if (browserPath == null) {
            log.error("Cannot find {} browser executable", browserType);
            return false;
        }
        log.info("Starting {} on port {} (headless={})", browserType, port, headless);
        log.info("Browser path: {}", browserPath);

        // Resolve user data directory
        String resolvedDir = resolveUserDataDir(browserType, userDataDir);
        if (resolvedDir == null) {
            log.error("Failed to resolve user data directory");
            return false;
        }
        log.info("User data directory: {}", resolvedDir);

        // Build command
        List<String> cmd = new ArrayList<>();
        cmd.add(browserPath);
        cmd.add("--remote-debugging-port=" + port);
        cmd.add("--user-data-dir=" + resolvedDir);
        cmd.add("--no-first-run");
        cmd.add("--no-default-browser-check");
        if (headless) cmd.add("--headless=new");

        log.info("Command: {}", String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            this.process = pb.start();
            this.port = port;
            log.info("Browser process started, PID: {}", process.pid());

            // Wait for CDP ready (max 30s)
            long deadline = System.currentTimeMillis() + 30_000;
            int checks = 0;
            while (System.currentTimeMillis() < deadline) {
                checks++;
                if (isCdpReady(port)) {
                    log.info("CDP ready after {}ms ({} checks)", System.currentTimeMillis() - (deadline - 30_000), checks);
                    Thread.sleep(500); // stability buffer
                    return true;
                }
                if (!process.isAlive()) {
                    log.error("Browser process exited with code: {}", process.exitValue());
                    return false;
                }
                if (checks % 4 == 0) {
                    log.info("Waiting for CDP... (check {})", checks);
                }
                Thread.sleep(500);
            }

            log.error("Browser did not become ready within 30s");
            stopBrowser();
            return false;
        } catch (Exception e) {
            log.error("Failed to start browser: {}", e.getMessage());
            return false;
        }
    }

    public void stopBrowser() {
        if (process != null && process.isAlive()) {
            log.info("Stopping browser...");
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("Browser stopped");
        }
        process = null;
    }

    private String resolveUserDataDir(String browserType, String userDataDir) {
        if (userDataDir != null && !userDataDir.isBlank()) {
            Path dir = Path.of(userDataDir);
            try { Files.createDirectories(dir); } catch (IOException e) { /* ignore */ }
            return userDataDir;
        }

        Path cdpProfileDir = Path.of(System.getProperty("user.home"),
                "AppData", "Roaming", "autoTool", "cdp_browser_profile");
        boolean isFirstTime = !Files.isDirectory(cdpProfileDir.resolve("Default"));

        if (isFirstTime) {
            log.info("CDP MODE - First time setup: copying browser profile...");
            Path sourceProfile = getDefaultUserDataDir(browserType);
            if (sourceProfile == null) {
                log.warn("No default browser profile found, creating empty CDP profile");
                try { Files.createDirectories(cdpProfileDir); } catch (IOException e) { /* ignore */ }
            } else {
                log.info("Copying from {} to {}", sourceProfile, cdpProfileDir);
                log.info("This may take 20-60 seconds for large profiles...");
                try {
                    copyProfileDir(sourceProfile, cdpProfileDir);
                    log.info("Profile copied successfully");
                } catch (IOException e) {
                    log.error("Failed to copy profile: {}", e.getMessage());
                    try { Files.createDirectories(cdpProfileDir); } catch (IOException ex) { /* ignore */ }
                }
            }
        } else {
            log.info("CDP MODE - Using existing automation profile: {}", cdpProfileDir);
        }
        return cdpProfileDir.toString();
    }

    private void copyProfileDir(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(dir);
                Files.createDirectories(target.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (shouldSkip(name)) return FileVisitResult.CONTINUE;
                try {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // Skip locked files silently (browser may hold locks)
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE; // Skip inaccessible files
            }
        });
    }

    private boolean shouldSkip(String filename) {
        if (SKIP_FILES.contains(filename)) return true;
        return filename.endsWith("-lock") || filename.endsWith(".tmp");
    }
}
