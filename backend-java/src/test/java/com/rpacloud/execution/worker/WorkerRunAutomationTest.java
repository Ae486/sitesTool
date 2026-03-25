package com.rpacloud.execution.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkerRunAutomationTest {

    @Test
    void parseArgs_chromiumHeadless() {
        var args = WorkerRunAutomation.parseArgs(new String[]{"chromium", "headless"});
        assertThat(args.browserType()).isEqualTo("chromium");
        assertThat(args.headless()).isTrue();
        assertThat(args.browserPath()).isNull();
        assertThat(args.internalApiUrl()).isNull();
    }

    @Test
    void parseArgs_firefoxHeaded_withApiUrl() {
        var args = WorkerRunAutomation.parseArgs(new String[]{
                "firefox", "headed", "--internal-api-url", "http://localhost:8000"});
        assertThat(args.browserType()).isEqualTo("firefox");
        assertThat(args.headless()).isFalse();
        assertThat(args.internalApiUrl()).isEqualTo("http://localhost:8000");
    }

    @Test
    void parseArgs_withBrowserPath() {
        var args = WorkerRunAutomation.parseArgs(new String[]{
                "chrome", "headless", "--browser-path", "/usr/bin/google-chrome"});
        assertThat(args.browserPath()).isEqualTo("/usr/bin/google-chrome");
    }

    @Test
    void parseArgs_tooFewArgs_throws() {
        assertThatThrownBy(() -> WorkerRunAutomation.parseArgs(new String[]{"chromium"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
