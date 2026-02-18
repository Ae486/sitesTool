package com.rpacloud.common.config;

import com.rpacloud.common.util.MdcTaskDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final RpaProperties rpaProperties;

    @Bean("automationTaskExecutor")
    public Executor automationTaskExecutor() {
        RpaProperties.Execution exec = rpaProperties.getExecution();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(exec.getWorkerPoolCore());
        executor.setMaxPoolSize(exec.getWorkerPoolMax());
        executor.setQueueCapacity(exec.getQueueCapacity());
        executor.setThreadNamePrefix("automation-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
