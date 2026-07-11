package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExternalApiProperties.class)
public class ExternalApiConfiguration {
}
