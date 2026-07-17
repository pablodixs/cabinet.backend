package com.scriptles.cabinet.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExternalInfoTaskConfiguration {
    @Bean(name = "externalInfoTaskExecutor")
    public ThreadPoolTaskExecutor externalInfoTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("external-info-");
        return executor;
    }
}
