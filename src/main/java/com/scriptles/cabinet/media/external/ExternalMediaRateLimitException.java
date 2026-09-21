package com.scriptles.cabinet.media.external;

import java.time.Duration;

public class ExternalMediaRateLimitException extends ExternalMediaException {
    private final Duration retryAfter;

    public ExternalMediaRateLimitException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public ExternalMediaRateLimitException(String message, Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
