package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.enums.MediaReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaReportRepository extends JpaRepository<MediaReport, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from MediaReport report where report.id = :reportId")
    Optional<MediaReport> findByIdForUpdate(@Param("reportId") UUID reportId);

    @EntityGraph(attributePaths = {"reportedBy", "reviewedBy"})
    Page<MediaReport> findAllByStatus(MediaReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reportedBy", "reviewedBy"})
    Page<MediaReport> findAllBy(Pageable pageable);
}
