package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.OrganizationRelationship;
import com.scriptles.cabinet.profile.enums.OrganizationType;
import java.util.UUID;
public record OrganizationResponse(UUID id, String name, OrganizationType type, OrganizationRelationship relationship, String countryCode, String logoUrl, UUID profileId, String profileHandle, boolean verified) {}
