package com.scriptles.cabinet.notifications.service;

public interface PushGateway {
    void send(NotificationDeliveryService.DeliveryTarget target);
    default boolean configured() { return true; }
}
