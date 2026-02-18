package com.rpacloud.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rpa")
public class RpaProperties {

    private Auth auth = new Auth();
    private Execution execution = new Execution();
    private Cors cors = new Cors();
    private Proxy proxy = new Proxy();
    private Llm llm = new Llm();

    @Data
    public static class Auth {
        private boolean disabled = false;
        private String secretKey = "change-me-in-production-at-least-32-chars!!";
        private int tokenExpireMinutes = 60;
    }

    @Data
    public static class Execution {
        private int processTimeoutSeconds = 300;
        private int workerPoolCore = 4;
        private int workerPoolMax = 8;
        private int queueCapacity = 100;
    }

    @Data
    public static class Cors {
        private String origins = "http://localhost:5173";
    }

    @Data
    public static class Proxy {
        private String proxyProviderUrl = "https://proxy.scdn.io/api/get_proxy.php";
        private long healthCheckIntervalMs = 300000L;
        private int cooldownSeconds = 60;
        private int maxConcurrentChecks = 10;
    }

    @Data
    public static class Llm {
        private int defaultRpm = 60;
        private int preFreezePadding = 2000;
        private String internalTokenSecret = "change-me-internal-secret-at-least-32-chars!!";
    }
}
