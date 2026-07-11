package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MovieDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovieDetailsRepository extends JpaRepository<MovieDetails, UUID> {
}
