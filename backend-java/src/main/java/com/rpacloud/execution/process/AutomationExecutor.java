package com.rpacloud.execution.process;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.llm.service.InternalTokenProvider;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.repository.ProxyRepository;
import com.rpacloud.proxy.service.ProxyPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${server.port:8000}")
    private int serverPort;

    public record TriggerResult(String status, String message, String executionId) {}

    public TriggerResult trigger(AutomationFlow flow) {
        if (processManager.isRunning(flow.getId())) {
            throw new BizException(ErrorCode.FLOW_ALREADY_RUNNING, "Flow is already running");
        }

        String executionId = UUID.randomUUID().toString().replace("-", "");
        List<String> cmd = buildCommand(flow, executionId);
        log.info("Triggering flow {} with execution_id {}", flow.getId(), executionId);

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

            // Delegate to separate bean so @Async is intercepted by Spring AOP proxy
            completionService.waitForCompletion(flow.getId(), process, executionId);

            return new TriggerResult("started", "Flow execution started", executionId);
        } catch (FlowAlreadyRunningException e) {
            throw new BizException(ErrorCode.FLOW_ALREADY_RUNNING, e.getMessage());
        } catch (Exception e) {
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

    private List<String> buildCommand(AutomationFlow flow, String executionId) {
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
        if (Boolean.TRUE.equals(flow.getUseProxy())) {
            String proxyUrl = resolveProxyUrl(flow.getProxyId());
            if (proxyUrl != null) {
                cmd.add("--proxy");
                cmd.add(proxyUrl);
            }
        }
        // LLM internal callback: always pass so subprocess can call /internal/llm/chat
        String internalToken = internalTokenProvider.createToken(
                1L, flow.getId(), executionId);
        cmd.add("--internal-api-url");
        cmd.add("http://localhost:" + serverPort);
        cmd.add("--internal-token");
        cmd.add(internalToken);
        return cmd;
    }

    private String resolveProxyUrl(Long proxyId) {
        if (proxyId != null) {
            return proxyRepository.flatMap(repo -> repo.findById(proxyId))
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .map(this::formatProxyUrl)
                    .orElse(null);
        }
        return proxyPoolService.flatMap(ProxyPoolService::getBestProxy)
                .map(this::formatProxyUrl)
                .orElse(null);
    }

    private String formatProxyUrl(Proxy proxy) {
        String protocol = proxy.getProtocol() != null ? proxy.getProtocol().toLowerCase() : "http";
        if ("socks5".equals(protocol)) return "socks5://" + proxy.getIp() + ":" + proxy.getPort();
        return "http://" + proxy.getIp() + ":" + proxy.getPort();
    }
}
