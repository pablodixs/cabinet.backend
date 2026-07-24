package com.scriptles.cabinet.media.enums;

import java.util.Locale;

public enum SupportedLocale {
    PT_BR("pt-BR"),
    EN_US("en-US");

    private final String tag;

    SupportedLocale(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public static SupportedLocale from(String value) {
        if (value == null || value.isBlank()) {
            return PT_BR;
        }
        String normalized = Locale.forLanguageTag(value).toLanguageTag();
        for (SupportedLocale locale : values()) {
            if (locale.tag.equalsIgnoreCase(normalized)) {
                return locale;
            }
        }
        throw new IllegalArgumentException("Locale não suportado. Use pt-BR ou en-US");
    }
}
