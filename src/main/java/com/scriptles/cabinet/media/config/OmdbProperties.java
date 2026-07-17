package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.omdb")
public record OmdbProperties(String baseUrl, String apiKey) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
