package com.scriptles.cabinet.user.importer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class LetterboxdImportConfiguration {
    @Bean(name = "letterboxdImportExecutor")
    public ThreadPoolTaskExecutor letterboxdImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("letterboxd-import-");
        return executor;
    }
}
