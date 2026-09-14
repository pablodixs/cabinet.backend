package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.*; import java.time.Instant; import java.util.UUID;
public record PostResponse(UUID id,UUID authorProfileId,PostType type,String title,String body,String linkUrl,Instant publishedAt) {}
