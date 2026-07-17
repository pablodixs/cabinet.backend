package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.notifications.event.NotificationChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NotificationStreamService {
    private static final long TIMEOUT_MILLIS = 30L * 60L * 1000L;
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger activeEmitters = new AtomicInteger();
    private final int maxEmittersPerUser;
    private final int maxEmittersTotal;

    @Autowired
    public NotificationStreamService(
            @Value("${app.notifications.sse.max-per-user:3}") int maxEmittersPerUser,
            @Value("${app.notifications.sse.max-total:1000}") int maxEmittersTotal
    ) {
        this.maxEmittersPerUser = positive(maxEmittersPerUser, "max-per-user");
        this.maxEmittersTotal = positive(maxEmittersTotal, "max-total");
    }

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        reserveGlobalSlot();
        try {
            emitters.compute(userId, (ignored, current) -> {
                Set<SseEmitter> userEmitters = current == null
                        ? ConcurrentHashMap.newKeySet() : current;
                if (userEmitters.size() >= maxEmittersPerUser) {
                    throw connectionLimit("Limite de conexões de notificações atingido para este usuário");
                }
                userEmitters.add(emitter);
                return userEmitters;
            });
        } catch (RuntimeException exception) {
            activeEmitters.decrementAndGet();
            throw exception;
        }
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ready"));
        } catch (IOException | IllegalStateException exception) {
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
        if (!userEmitters.remove(emitter)) return;
        activeEmitters.decrementAndGet();
        if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
    }

    private void reserveGlobalSlot() {
        int active = activeEmitters.incrementAndGet();
        if (active > maxEmittersTotal) {
            activeEmitters.decrementAndGet();
            throw connectionLimit("Limite global de conexões de notificações atingido");
        }
    }

    private ApiException connectionLimit(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SSE_CONNECTION_LIMIT", message);
    }

    private int positive(int value, String property) {
        if (value < 1) {
            throw new IllegalArgumentException("app.notifications.sse." + property + " must be positive");
        }
        return value;
    }
}
