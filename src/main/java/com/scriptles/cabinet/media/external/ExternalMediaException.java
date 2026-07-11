package com.scriptles.cabinet.media.external;

public class ExternalMediaException extends RuntimeException {
    public ExternalMediaException(String message) {
        super(message);
    }

    public ExternalMediaException(String message, Throwable cause) {
        super(message, cause);
    }
}
