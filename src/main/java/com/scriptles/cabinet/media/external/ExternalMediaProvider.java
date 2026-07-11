package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.util.List;
import java.util.Optional;

public interface ExternalMediaProvider {
    ExternalSource source();

    boolean supports(MediaType mediaType);

    List<ExternalMedia> search(MediaType mediaType, String query);

    Optional<ExternalMedia> findById(MediaType mediaType, String externalId);
}
