package com.scriptles.cabinet.media.translation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class LocalizedResponse {
    private LocalizedResponse() {
    }

    public static <T> ResponseEntity<T> ok(T body, String locale, boolean varyByAcceptLanguage) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_LANGUAGE, locale);
        if (varyByAcceptLanguage) {
            builder.header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        }
        return builder.body(body);
    }

    public static <T> ResponseEntity<T> created(T body, String locale) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CONTENT_LANGUAGE, locale)
                .body(body);
    }
}
