package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.util.List;
import java.util.Optional;

public interface ExternalMediaProvider {
    ExternalSource source();

    boolean supports(MediaType mediaType);

    List<ExternalMedia> search(MediaType mediaType, String query);

    default List<ExternalMedia> search(MediaType mediaType, String query, String language) {
        return search(mediaType, query);
    }

    default List<ExternalMedia> search(MediaType mediaType, String query, String language, int offset, int limit) {
        return search(mediaType, query, language);
    }

    default List<ExternalMedia> searchAll(String query, String language) {
        throw new UnsupportedOperationException("Global search is unavailable for " + source());
    }

    default List<ExternalMedia> searchAll(String query, String language, int offset, int limit) {
        return searchAll(query, language);
    }

    Optional<ExternalMedia> findById(MediaType mediaType, String externalId);

    default Optional<ExternalMedia> findById(MediaType mediaType, String externalId, String language) {
        return findById(mediaType, externalId);
    }

    default Optional<ExternalMedia> findCoreById(MediaType mediaType, String externalId, String language) {
        return findById(mediaType, externalId, language);
    }

    default Optional<ExternalMedia> findEnrichmentById(MediaType mediaType, String externalId, String language) {
        return findById(mediaType, externalId, language);
    }
}
