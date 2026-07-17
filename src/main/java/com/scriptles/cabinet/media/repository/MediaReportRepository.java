package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.enums.MediaReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaReportRepository extends JpaRepository<MediaReport, UUID> {
    @EntityGraph(attributePaths = {"reportedBy", "reviewedBy"})
    Page<MediaReport> findAllByStatus(MediaReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reportedBy", "reviewedBy"})
    Page<MediaReport> findAllBy(Pageable pageable);
}
