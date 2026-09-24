package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.*; import jakarta.validation.constraints.*;
public record CreateHQRequest(@NotBlank @Size(max=80) String handle,@NotBlank @Size(max=200) String name,@NotNull HQType type,@NotBlank @Size(max=30) String source,@NotBlank @Size(max=255) String externalId,@Size(max=500) String websiteUrl,@Size(max=2) String countryCode,@Size(max=500) String logoUrl,@Size(max=5000) String description, @Email String operatorEmail, @Size(max=120) String operatorName, String operatorPassword) {}
