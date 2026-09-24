package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;

import java.util.UUID;

public interface CreditDetailsProjection {
    UUID getPersonId();
    String getName();
    CreditRole getRole();
    String getCharacterName();
    Integer getPosition();
    String getImageUrl();
    ExternalSource getSource();
    String getExternalId();
}
