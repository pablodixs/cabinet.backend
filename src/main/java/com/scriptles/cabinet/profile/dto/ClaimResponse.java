package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.HQClaimRequestStatus; import java.time.Instant; import java.util.UUID;
public record ClaimResponse(UUID id,UUID hqProfileId,HQClaimRequestStatus status,String organizationEmail,String evidenceUrl,String message,Instant createdAt) {}
