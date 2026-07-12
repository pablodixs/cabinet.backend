package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ExternalApiProperties.class)
public class ExternalApiConfiguration {
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
