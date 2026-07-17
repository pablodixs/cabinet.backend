package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.notifications.event.NotificationChangedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationStreamService {
    private static final long TIMEOUT_MILLIS = 30L * 60L * 1000L;
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ready"));
        } catch (IOException exception) {
            cleanup.run();
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationChanged(NotificationChangedEvent event) {
        send(event.recipientId(), "notifications-changed", "refresh");
    }

    @Scheduled(fixedRate = 25_000L)
    public void heartbeat() {
        emitters.keySet().forEach(userId -> send(userId, "heartbeat", "ping"));
    }

    private void send(UUID userId, String eventName, String data) {
        Set<SseEmitter> userEmitters = emitters.getOrDefault(userId, Set.of());
        userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException exception) {
                remove(userId, emitter);
            }
        });
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
    }
}
