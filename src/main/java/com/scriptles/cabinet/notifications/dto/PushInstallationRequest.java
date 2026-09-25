package com.scriptles.cabinet.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushInstallationRequest(@NotBlank @Size(min = 20, max = 4096) String token) { }
