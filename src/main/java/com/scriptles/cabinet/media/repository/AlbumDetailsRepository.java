package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AlbumDetailsRepository extends JpaRepository<AlbumDetails, UUID> {
}
