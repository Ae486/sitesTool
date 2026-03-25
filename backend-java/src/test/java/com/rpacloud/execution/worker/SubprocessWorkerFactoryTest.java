package com.rpacloud.execution.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SubprocessWorkerFactoryTest {

    @Test
    void buildCommand_defaultChromiumHeadless() {
        var factory = new SubprocessWorkerFactory("http://localhost:8000");
        var key = new WorkerKey("chromium", true, null);

        List<String> cmd = factory.buildCommand(key);

        assertThat(cmd.get(0)).satisfies(
                java -> assertThat(java).containsIgnoringCase("java"));
        // Should contain classpath
        assertThat(cmd).contains("-cp");
        // Should contain WorkerRunAutomation class (or PropertiesLauncher for fat-jar)
        assertThat(cmd.stream().anyMatch(
                s -> s.contains("WorkerRunAutomation") || s.contains("PropertiesLauncher"))).isTrue();
        // Static args
        assertThat(cmd).contains("chromium");
        assertThat(cmd).contains("headless");
        assertThat(cmd).contains("--internal-api-url", "http://localhost:8000");
    }

    @Test
    void buildCommand_firefoxHeaded() {
        var factory = new SubprocessWorkerFactory(null);
        var key = new WorkerKey("firefox", false, null);

        List<String> cmd = factory.buildCommand(key);
        assertThat(cmd).contains("firefox");
        assertThat(cmd).contains("headed");
        assertThat(cmd).doesNotContain("--internal-api-url");
    }

    @Test
    void buildCommand_withUserDataDir() {
        var factory = new SubprocessWorkerFactory("http://localhost:8000");
        var key = new WorkerKey("chrome", true, "/data/cdp-profile");

        List<String> cmd = factory.buildCommand(key);
        assertThat(cmd).contains("--browser-path", "/data/cdp-profile");
    }
}
