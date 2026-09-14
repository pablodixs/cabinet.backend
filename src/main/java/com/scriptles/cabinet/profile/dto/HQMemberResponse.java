package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import java.time.Instant;
import java.util.UUID;
public record HQMemberResponse(UUID id, UUID accountId, String username, String displayName, HQMemberRole role, Instant createdAt) {}
