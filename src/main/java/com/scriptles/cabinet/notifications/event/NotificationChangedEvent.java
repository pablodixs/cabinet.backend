package com.scriptles.cabinet.notifications.event;

import java.util.UUID;

public record NotificationChangedEvent(UUID recipientId) {
}
