package com.scriptles.cabinet.user.enums;

public enum UserRole {
    USER,
    MODERATOR,
    ADMIN;

    public boolean canModerate() {
        return this == MODERATOR || this == ADMIN;
    }
}
