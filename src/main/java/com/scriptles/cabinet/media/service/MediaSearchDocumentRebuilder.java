package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.repository.MediaSearchDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaSearchDocumentRebuilder {
    private final MediaSearchDocumentRepository repository;

    @Transactional
    public void rebuild(UUID mediaId) {
        repository.rebuild(mediaId);
    }
}
