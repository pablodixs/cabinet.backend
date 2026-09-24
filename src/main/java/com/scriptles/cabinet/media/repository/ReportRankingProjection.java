package com.scriptles.cabinet.media.repository;

import java.util.UUID;

public interface ReportRankingProjection {
    UUID getPersonId();

    String getPersonName();

    String getPersonImageUrl();

    Long getEventCount();

    Long getEligibleEventCount();

    Long getAttributedEventCount();
}
