package com.scriptles.cabinet.user.service;

public class RegistrationConflictException extends RuntimeException {
    private final String field;

    public RegistrationConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
