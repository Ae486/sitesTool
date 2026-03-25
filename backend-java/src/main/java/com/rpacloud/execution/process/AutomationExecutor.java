package com.rpacloud.execution.process;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.execution.worker.WorkerAcquireTimeoutException;
import com.rpacloud.execution.worker.WorkerHandle;
import com.rpacloud.execution.worker.WorkerKey;
import com.rpacloud.execution.worker.WorkerPool;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.llm.service.InternalTokenProvider;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.repository.ProxyRepository;
import com.rpacloud.proxy.service.ProxyAcquireTimeoutException;
import com.rpacloud.proxy.service.ProxyLease;
import com.rpacloud.proxy.service.ProxyLeasePool;
import com.rpacloud.proxy.service.ProxyPoolService;
import com.rpacloud.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationExecutor {

    private final ProcessManager processManager;
    private final FlowRepository flowRepository;
    private final ExecutionCompletionService completionService;
    private final Optional<ProxyPoolService> proxyPoolService;
    private final Optional<ProxyRepository> proxyRepository;
    private final InternalTokenProvider internalTokenProvider;
    private final Optional<ProxyLeasePool> proxyLeasePool;
    private final Optional<WorkerPool> workerPool;
    private final RpaProperties rpaProperties;

    @Value("${server.port:8000}")
    private int serverPort;

    public record TriggerResult(String status, String message, String executionId) {}

    public TriggerResult trigger(AutomationFlow flow) {
        if (processManager.isRunning(flow.getId())) {
            throw new BizException(ErrorCode.FLOW_ALREADY_RUNNING, "Flow is already running");
        }

        String executionId = UUID.randomUUID().toString().replace("-", "");

        // Resolve proxy: pool checkout for auto, direct lookup for manual
        String proxyUrl = null;
        boolean leaseFromPool = false;
        if (Boolean.TRUE.equals(flow.getUseProxy())) {
            if (flow.getProxyId() != null) {
                proxyUrl = resolveManualProxy(flow.getProxyId());
            } else {
                ProxyLease lease = checkoutFromPool(executionId);
                if (lease != null) {
                    proxyUrl = lease.getUrl();
                    leaseFromPool = true;
                }
            }
        }

        // Pooled path: Worker pool enabled and flow is not CDP mode
        boolean usePool = workerPool.isPresent() && !Boolean.TRUE.equals(flow.getUseCdpMode());

        if (usePool) {
            return triggerPooled(flow, executionId, proxyUrl, leaseFromPool);
        }
        return triggerSubprocess(flow, executionId, proxyUrl, leaseFromPool);
    }

    private TriggerResult triggerPooled(AutomationFlow flow, String executionId,
                                         String proxyUrl, boolean leaseFromPool) {
        long flowId = flow.getId();
        try {
            WorkerKey key = new WorkerKey(flow.getBrowserType(),
                    Boolean.TRUE.equals(flow.getHeadless()), null);
            long acquireMs = rpaProperties.getWorkerPool().getAcquireTimeoutMs();
            WorkerHandle worker = workerPool.get().borrow(key, Duration.ofMillis(acquireMs));

            processManager.registerWorker(flowId, worker);
            flow.setLastStatus(FlowStatus.running);
            flowRepository.save(flow);

            String internalToken = internalTokenProvider.createToken(
                    resolveCurrentUserId(), flowId, executionId);
            log.info("Triggering flow {} via worker pool (worker={}), execution_id {}",
                    flowId, worker.getId(), executionId);

            completionService.waitForPooledCompletion(flowId, worker, flow.getDsl(),
                    executionId, proxyUrl, internalToken, leaseFromPool);

            return new TriggerResult("started", "Flow execution started (pooled)", executionId);
        } catch (WorkerAcquireTimeoutException e) {
            if (leaseFromPool) returnLeaseOnFailure(executionId);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "No worker available: " + e.getMessage());
        } catch (FlowAlreadyRunningException e) {
            if (leaseFromPool) returnLeaseOnFailure(executionId);
            throw new BizException(ErrorCode.FLOW_ALREADY_RUNNING, e.getMessage());
        } catch (Exception e) {
            if (leaseFromPool) returnLeaseOnFailure(executionId);
            log.error("Failed to trigger pooled flow {}: {}", flowId, e.getMessage());
            processManager.unregisterWorker(flowId);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to start pooled execution: " + e.getMessage());
        }
    }

    private TriggerResult triggerSubprocess(AutomationFlow flow, String executionId,
                                             String proxyUrl, boolean leaseFromPool) {
        List<String> cmd = buildCommand(flow, executionId, proxyUrl);
        log.info("Triggering flow {} via subprocess, execution_id {}", flow.getId(), executionId);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            try (var os = process.getOutputStream()) {
                os.write(flow.getDsl().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            processManager.register(flow.getId(), process);

            flow.setLastStatus(FlowStatus.running);
            flowRepository.save(flow);

            completionService.waitForCompletion(flow.getId(), process, executionId, leaseFromPool);

            return new TriggerResult("started", "Flow execution started", executionId);
        } catch (FlowAlreadyRunningException e) {
            if (leaseFromPool) returnLeaseOnFailure(executionId);
            throw new BizException(ErrorCode.FLOW_ALREADY_RUNNING, e.getMessage());
        } catch (Exception e) {
            if (leaseFromPool) returnLeaseOnFailure(executionId);
            log.error("Failed to start process for flow {}: {}", flow.getId(), e.getMessage());
            processManager.unregister(flow.getId());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to start execution: " + e.getMessage());
        }
    }

    public TriggerResult stop(AutomationFlow flow) {
        if (!processManager.isRunning(flow.getId())) {
            return new TriggerResult("idle", "Flow is not running", null);
        }
        boolean stopped = processManager.forceStop(flow.getId());
        return stopped
                ? new TriggerResult("stopped", "Flow execution stopped", null)
                : new TriggerResult("failed", "Failed to stop flow", null);
    }

    public boolean isRunning(long flowId) {
        return processManager.isRunning(flowId);
    }

    public List<Long> getRunningFlowIds() {
        return processManager.getRunningFlowIds();
    }

    private static final String RUNNER_CLASS = "com.rpacloud.execution.engine.RunAutomation";
    private static final String PROPERTIES_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";

    private List<String> buildCommand(AutomationFlow flow, String executionId, String proxyUrl) {
        String javaPath = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);

        boolean fatJar = classpath.endsWith(".jar") && !classpath.contains(java.io.File.pathSeparator);
        if (fatJar) {
            // Fat-jar: use Spring Boot PropertiesLauncher to resolve BOOT-INF/lib/*
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("-Dloader.main=" + RUNNER_CLASS);
            cmd.add(PROPERTIES_LAUNCHER);
        } else {
            // IDE / exploded classpath
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add(RUNNER_CLASS);
        }
        cmd.add(String.valueOf(flow.getId()));
        cmd.add("--dsl-stdin");
        cmd.add(flow.getHeadless() ? "--headless" : "--headed");
        cmd.add("--browser");
        cmd.add(flow.getBrowserType());
        cmd.add("--execution-id");
        cmd.add(executionId);
        if (flow.getBrowserPath() != null && !flow.getBrowserPath().isBlank()) {
            cmd.add("--browser-path");
            cmd.add(flow.getBrowserPath());
        }
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            cmd.add("--proxy");
            cmd.add(proxyUrl);
        }
        if (Boolean.TRUE.equals(flow.getUseCdpMode())) {
            cmd.add("--use-cdp-mode");
            cmd.add("--cdp-port");
            cmd.add(String.valueOf(flow.getCdpPort() != null ? flow.getCdpPort() : 9222));
            if (flow.getCdpUserDataDir() != null && !flow.getCdpUserDataDir().isBlank()) {
                cmd.add("--cdp-user-data-dir");
                cmd.add(flow.getCdpUserDataDir());
            }
        }
        // LLM internal callback: always pass so subprocess can call /internal/llm/chat
        String internalToken = internalTokenProvider.createToken(
                resolveCurrentUserId(), flow.getId(), executionId);
        cmd.add("--internal-api-url");
        cmd.add("http://localhost:" + serverPort);
        cmd.add("--internal-token");
        cmd.add(internalToken);
        return cmd;
    }

    private String resolveManualProxy(Long proxyId) {
        return proxyRepository.flatMap(repo -> repo.findById(proxyId))
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(this::formatProxyUrl)
                .orElse(null);
    }

    private ProxyLease checkoutFromPool(String executionId) {
        return proxyLeasePool.map(pool -> {
            try {
                long timeoutMs = rpaProperties.getProxy().getProxyAcquireTimeoutMs();
                return pool.checkout(executionId, Duration.ofMillis(timeoutMs));
            } catch (ProxyAcquireTimeoutException e) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, e.getMessage());
            }
        }).orElse(null);
    }

    private void returnLeaseOnFailure(String executionId) {
        proxyLeasePool.ifPresent(pool ->
                pool.returnProxy(executionId, new com.rpacloud.proxy.service.ProxyUseResult(false, 0, List.of())));
    }

    private String formatProxyUrl(Proxy proxy) {
        String protocol = proxy.getProtocol() != null ? proxy.getProtocol().toLowerCase() : "http";
        if ("socks5".equals(protocol)) return "socks5://" + proxy.getIp() + ":" + proxy.getPort();
        return "http://" + proxy.getIp() + ":" + proxy.getPort();
    }

    private static long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L; // Quartz scheduled jobs run without SecurityContext
    }
}
