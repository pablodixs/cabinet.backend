package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enums.ExternalSource;

import java.util.Optional;

public interface ArtistIdentityEnricher {
    Optional<String> findWikidataId(ExternalSource source, String externalId);
}
