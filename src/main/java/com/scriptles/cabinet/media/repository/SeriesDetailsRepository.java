package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.SeriesDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeriesDetailsRepository extends JpaRepository<SeriesDetails, UUID> {
}
