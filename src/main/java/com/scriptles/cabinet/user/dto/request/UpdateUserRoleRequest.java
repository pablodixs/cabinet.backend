package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole role) {
}
