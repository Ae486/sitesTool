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
    private Storage storage = new Storage();
    private GeoIp geoIp = new GeoIp();
    private WorkerPool workerPool = new WorkerPool();

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
        private String healthCheckUrl = "http://myip.ipip.net";
        private String healthCheckUrlFallback = "http://api.ipify.org";
        private long proxyAcquireTimeoutMs = 30000L;
        private long proxyMaxLeaseMs = 360000L;
    }

    @Data
    public static class Llm {
        private int defaultRpm = 60;
        private int preFreezePadding = 2000;
        private String internalTokenSecret = "change-me-internal-secret-at-least-32-chars!!";
    }

    @Data
    public static class Storage {
        private String basePath = "data/storage";
        private long maxFileSizeBytes = 50 * 1024 * 1024;
        private long userQuotaBytes = 1024L * 1024 * 1024;
        private int screenshotRetentionDays = 7;
    }

    @Data
    public static class GeoIp {
        private String baseUrl = "http://ip-api.com";
    }

    @Data
    public static class WorkerPool {
        private boolean enabled = false;
        private int globalMaxActive = 8;
        private int maxActivePerKey = 4;
        private int maxIdleMinutes = 10;
        private int maxTasksPerWorker = 100;
        private int maxLifetimeMinutes = 60;
        private long acquireTimeoutMs = 30000L;
    }
}
