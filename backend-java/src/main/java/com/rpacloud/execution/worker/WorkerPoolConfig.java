package com.rpacloud.execution.worker;

import com.rpacloud.common.config.RpaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "rpa.worker-pool.enabled", havingValue = "true")
public class WorkerPoolConfig {

    @Bean
    SubprocessWorkerFactory subprocessWorkerFactory(@Value("${server.port:8000}") int serverPort) {
        return new SubprocessWorkerFactory("http://localhost:" + serverPort);
    }

    @Bean
    WorkerPool workerPool(SubprocessWorkerFactory factory, RpaProperties props) {
        return new WorkerPool(factory, props.getWorkerPool());
    }
}
