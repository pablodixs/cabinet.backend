package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.importer.LetterboxdImportJob;

public record ActiveLetterboxdImportResponse(
        boolean active,
        LetterboxdImportJobResponse job
) {
    public static ActiveLetterboxdImportResponse from(LetterboxdImportJob job) {
        return new ActiveLetterboxdImportResponse(true, LetterboxdImportJobResponse.from(job));
    }

    public static ActiveLetterboxdImportResponse none() {
        return new ActiveLetterboxdImportResponse(false, null);
    }
}
