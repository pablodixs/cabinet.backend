package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.TrackDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TrackDetailsRepository extends JpaRepository<TrackDetails, UUID> {
}
