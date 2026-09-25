package com.scriptles.cabinet.notifications.dto;

import com.scriptles.cabinet.notifications.enums.NotificationType;
public record PushPreferenceResponse(NotificationType type, boolean enabled) { }
