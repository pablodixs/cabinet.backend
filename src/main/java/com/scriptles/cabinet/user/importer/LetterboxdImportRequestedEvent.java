package com.scriptles.cabinet.user.importer;

import java.util.UUID;

public record LetterboxdImportRequestedEvent(UUID jobId, Action action) {
    public enum Action { MATCH, APPLY }
}
