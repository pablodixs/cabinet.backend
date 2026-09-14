package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQType;
import java.util.UUID;
public record HQAdminSummaryResponse(UUID profileId,String handle,String displayName,String avatarUrl,HQType type,HQClaimStatus claimStatus,boolean verified,long followers) {}
