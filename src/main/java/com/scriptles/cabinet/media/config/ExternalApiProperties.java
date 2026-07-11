package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external")
public record ExternalApiProperties(Tmdb tmdb, GoogleBooks googleBooks) {

    public record Tmdb(String baseUrl, String apiKey) {
    }

    public record GoogleBooks(String baseUrl) {
    }
}
