package com.scriptles.cabinet.profile.dto;
import jakarta.validation.constraints.*;
public record ClaimRequest(@NotBlank @Email String organizationEmail, @Size(max=500) String evidenceUrl, @Size(max=5000) String message) {}
