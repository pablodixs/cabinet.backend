package com.scriptles.cabinet.user.importer;

public enum LetterboxdImportJobState {
    PARSING,
    MATCHING,
    READY,
    IMPORTING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED || this == CANCELLED;
    }

    public boolean active() {
        return !terminal();
    }
}
