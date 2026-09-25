package com.scriptles.cabinet.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.scriptles.cabinet.notifications.service.NotificationDeliveryService;
import com.scriptles.cabinet.notifications.service.NotificationDeliveryWorker.PushDeliveryException;
import com.scriptles.cabinet.notifications.service.PushGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "firebase.messaging.enabled", havingValue = "true")
public class FirebaseMessagingConfiguration {
    @Bean
    FirebaseApp cabinetFirebaseApp(org.springframework.core.env.Environment environment) throws IOException {
        String credentialsPath = environment.getProperty("firebase.messaging.credentials-file", "").trim();
        GoogleCredentials credentials = credentialsPath.isEmpty()
                ? GoogleCredentials.getApplicationDefault()
                : GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        String projectId = environment.getProperty("firebase.messaging.project-id", "").trim();
        if (!projectId.isEmpty()) options.setProjectId(projectId);
        return FirebaseApp.initializeApp(options.build(), "cabinet-push");
    }

    @Bean
    PushGateway firebasePushGateway(FirebaseApp app) {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
        return target -> {
            Message message = Message.builder()
                    .setToken(target.token())
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle("Cabinet")
                            .setBody("Você tem uma nova notificação no Cabinet.")
                            .build())
                    .putData("notificationId", target.notificationId().toString())
                    .putData("type", target.type().name())
                    .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setSound("default").build()).build())
                    .build();
            try {
                messaging.send(message);
            } catch (FirebaseMessagingException failure) {
                boolean invalid = failure.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || failure.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT;
                throw new PushDeliveryException("FCM " + failure.getMessagingErrorCode(), invalid, failure);
            }
        };
    }
}
