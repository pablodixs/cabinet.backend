package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external")
public record ExternalApiProperties(
        Tmdb tmdb,
        GoogleBooks googleBooks,
        MusicBrainz musicbrainz,
        TheAudioDb theAudioDb
) {

    public record Tmdb(String baseUrl, String apiKey, String accessToken) {
    }

    public record GoogleBooks(String baseUrl, String apiKey) {
    }

    public record MusicBrainz(String baseUrl, String userAgent) {
    }

    public record TheAudioDb(String baseUrl, String apiKey) {
    }
}
