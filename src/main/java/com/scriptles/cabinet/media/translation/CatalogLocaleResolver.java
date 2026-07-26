package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class CatalogLocaleResolver {
    public static final String DEFAULT_LOCALE = "pt-BR";

    public SupportedLocale resolve(String localeOverride, String acceptLanguage) {
        if (localeOverride != null && !localeOverride.isBlank()) {
            return parseExplicit(localeOverride);
        }
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return SupportedLocale.PT_BR;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                SupportedLocale supported = match(range.getRange());
                if (supported != null) {
                    return supported;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed optional header must not make the catalog unavailable.
        }
        return SupportedLocale.PT_BR;
    }

    public SupportedLocale parseExplicit(String locale) {
        SupportedLocale supported = match(locale);
        if (supported == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_LOCALE",
                    "Locale não suportado. Use pt-BR ou en-US"
            );
        }
        return supported;
    }

    public String normalize(String locale) {
        return parseExplicit(locale).tag();
    }

    public List<String> fallbackChain(String requestedLocale) {
        SupportedLocale requested = parseExplicit(requestedLocale);
        return Arrays.stream(SupportedLocale.values())
                .sorted((left, right) -> {
                    if (left == requested) return -1;
                    if (right == requested) return 1;
                    return left.tag().compareTo(right.tag());
                })
                .map(SupportedLocale::tag)
                .toList();
    }

    private SupportedLocale match(String value) {
        if (value == null || value.isBlank() || "*".equals(value.trim())) {
            return null;
        }
        Locale parsed = Locale.forLanguageTag(value.trim().replace('_', '-'));
        String tag = parsed.toLanguageTag();
        for (SupportedLocale supported : SupportedLocale.values()) {
            if (supported.tag().equalsIgnoreCase(tag)) {
                return supported;
            }
        }
        if ("pt".equalsIgnoreCase(parsed.getLanguage())) {
            return SupportedLocale.PT_BR;
        }
        if ("en".equalsIgnoreCase(parsed.getLanguage())) {
            return SupportedLocale.EN_US;
        }
        return null;
    }
}
