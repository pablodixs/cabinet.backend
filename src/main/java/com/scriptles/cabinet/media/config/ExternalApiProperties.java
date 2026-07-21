package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external")
public record ExternalApiProperties(
        Tmdb tmdb,
        GoogleBooks googleBooks,
        MusicBrainz musicbrainz,
        TheAudioDb theAudioDb,
        Wikidata wikidata
) {

    public record Tmdb(String baseUrl, String apiKey, String accessToken) {
    }

    public record GoogleBooks(String baseUrl, String apiKey) {
    }

    public record MusicBrainz(String baseUrl, String userAgent) {
    }

    public record TheAudioDb(String baseUrl, String apiKey) {
    }

    public record Wikidata(String sparqlUrl, String userAgent, Duration readTimeout) {
        private static final String DEFAULT_SPARQL_URL = "https://query.wikidata.org/sparql";
        private static final String DEFAULT_USER_AGENT = "cabinet/1.0 (https://github.com/scriptles/cabinet)";
        private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

        public Wikidata {
            sparqlUrl = sparqlUrl == null || sparqlUrl.isBlank() ? DEFAULT_SPARQL_URL : sparqlUrl;
            userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent;
            readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
        }

        public static Wikidata defaults() {
            return new Wikidata(DEFAULT_SPARQL_URL, DEFAULT_USER_AGENT, DEFAULT_READ_TIMEOUT);
        }
    }
}
