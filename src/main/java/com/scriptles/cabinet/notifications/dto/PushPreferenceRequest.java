package com.scriptles.cabinet.notifications.dto;

import com.scriptles.cabinet.notifications.enums.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PushPreferenceRequest(@NotEmpty List<@Valid Preference> preferences) {
    public record Preference(@NotNull NotificationType type, boolean enabled) { }
}
