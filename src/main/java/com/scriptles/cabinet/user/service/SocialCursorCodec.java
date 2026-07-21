package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

@Component
public class SocialCursorCodec {
    public String encode(Instant timestamp, UUID userId) {
        String value = timestamp + "|" + userId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            int separator = decoded.indexOf('|');
            if (separator <= 0 || separator == decoded.length() - 1
                    || decoded.indexOf('|', separator + 1) >= 0) {
                throw invalidCursor();
            }
            return new Position(
                    Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1))
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private ApiException invalidCursor() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CURSOR",
                "O cursor informado é inválido"
        );
    }

    public record Position(Instant timestamp, UUID userId) {
    }
}
