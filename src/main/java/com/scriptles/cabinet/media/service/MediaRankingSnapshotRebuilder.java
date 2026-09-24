package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.repository.MediaRankingSnapshotRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaRankingSnapshotRebuilder {
    private final MediaRankingSnapshotRepository snapshotRepository;
    private final MediaRepository mediaRepository;

    @Transactional
    public void rebuild(UUID mediaId) {
        if (!mediaRepository.existsById(mediaId)) return;
        snapshotRepository.rebuild(mediaId);
    }
}
