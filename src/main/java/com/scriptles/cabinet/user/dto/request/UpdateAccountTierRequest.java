package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.AccountTier;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountTierRequest(@NotNull AccountTier accountTier) {
}
