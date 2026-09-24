package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.enums.CreditRole;

import java.util.UUID;

/** Minimal row used by recommendation and interest calculations. */
public interface CreditScoringProjection {
    UUID getMediaId();
    UUID getPersonId();
    String getPersonName();
    CreditRole getRole();
    Integer getPosition();
}
