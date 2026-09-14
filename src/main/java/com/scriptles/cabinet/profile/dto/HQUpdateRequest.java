package com.scriptles.cabinet.profile.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
public record HQUpdateRequest(@Size(max=120) String displayName,@Size(max=5000) String bio,@Size(max=500) String avatarUrl,@Size(max=500) String backdropUrl,@Size(max=500) String websiteUrl,@Email @Size(max=254) String contactEmail,@Size(max=160) String legalName,@Size(max=2) String countryCode) {}
