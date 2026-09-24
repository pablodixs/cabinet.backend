package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.enums.CreditRole;

import java.util.UUID;

/** Headline fields shown on cards and list rows. */
public interface CreditHeadlineProjection {
    UUID getMediaId();
    String getPersonName();
    CreditRole getRole();
    Integer getPosition();
}
