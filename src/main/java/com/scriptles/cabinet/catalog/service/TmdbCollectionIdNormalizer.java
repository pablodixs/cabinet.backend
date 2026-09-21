package com.scriptles.cabinet.catalog.service;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TmdbCollectionIdNormalizer {
    private static final Pattern ID = Pattern.compile("^[1-9][0-9]*$");
    private static final Pattern URL_PATH = Pattern.compile("^/collection/([1-9][0-9]*)(?:-[^/]*)?/?$");

    private TmdbCollectionIdNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Informe um ID ou URL de coleção TMDB");
        }
        String input = value.trim();
        if (ID.matcher(input).matches()) return validated(input);
        final URI uri;
        try {
            uri = URI.create(input);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("URL de coleção TMDB inválida");
        }
        String host = uri.getHost();
        if (!"themoviedb.org".equalsIgnoreCase(host) && !"www.themoviedb.org".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("Use uma URL de coleção do themoviedb.org");
        }
        Matcher matcher = URL_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (!matcher.matches()) throw new IllegalArgumentException("A URL não identifica uma coleção TMDB");
        return validated(matcher.group(1));
    }

    private static String validated(String id) {
        try {
            if (Long.parseLong(id) > 0) return id;
        } catch (NumberFormatException ignored) {
            // IDs outside the provider's supported numeric range are invalid.
        }
        throw new IllegalArgumentException("ID de coleção TMDB inválido");
    }
}
