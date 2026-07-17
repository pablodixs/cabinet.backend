package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaMetadataRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaMetadataRevisionRepository extends JpaRepository<MediaMetadataRevision, UUID> {
}
