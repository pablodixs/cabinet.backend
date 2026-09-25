package com.scriptles.cabinet.notifications.entity;

import com.scriptles.cabinet.notifications.enums.NotificationType;
import java.io.Serializable;
import java.util.UUID;

public record PushPreferenceId(UUID user, NotificationType notificationType) implements Serializable { }
