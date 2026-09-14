package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.ProfileType; import java.util.UUID;
public record ProfileSearchResponse(UUID id, ProfileType type, String handle, String displayName, String avatarUrl, boolean verified, String organizationName) {}
