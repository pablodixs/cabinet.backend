package com.scriptles.cabinet.common.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;
import java.net.URI;

/** Versioned, portable rich-text document used by the iOS client. */
public final class RichTextDocument {
    private RichTextDocument() {}

    public static String validateAndExtractText(String json, boolean allowLinks) {
        try { return validateAndExtractText(parse(json), allowLinks); }
        catch (Exception error) { invalid(); return ""; }
    }

    public static JsonNode parse(String json) {
        try { return new ObjectMapper().readTree(json); }
        catch (Exception error) { invalid(); return null; }
    }

    public static String validateAndExtractText(JsonNode document, boolean allowLinks) {
        if (document == null || !document.isObject() || document.path("version").asInt(-1) != 1
                || !document.path("blocks").isArray() || document.path("blocks").isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_RICH_TEXT", "Documento de texto formatado inválido");
        }
        StringBuilder plain = new StringBuilder();
        if (document.path("blocks").size() > 500) invalid();
        for (JsonNode block : document.path("blocks")) {
            String type = block.path("type").asText();
            if (!Set.of("paragraph", "quote").contains(type) || !block.path("children").isArray() || block.path("children").size() > 1000) invalid();
            if (!plain.isEmpty()) plain.append('\n');
            for (JsonNode run : block.path("children")) {
                if (!run.path("text").isTextual()) invalid();
                plain.append(run.path("text").asText());
                JsonNode marks = run.path("marks");
                if (!marks.isArray()) invalid();
                for (JsonNode mark : marks) if (!Set.of("bold", "italic").contains(mark.asText())) invalid();
                JsonNode link = run.get("link");
                if (link != null && !link.isNull()) {
                    String href = link.isTextual() ? link.asText() : "";
                    try {
                        URI uri = URI.create(href);
                        String scheme = uri.getScheme();
                        if (!allowLinks || uri.getHost() == null || scheme == null
                                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) invalid();
                    } catch (IllegalArgumentException error) { invalid(); }
                }
            }
        }
        if (plain.length() > 20_000) invalid();
        return plain.toString();
    }

    private static void invalid() {
        throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_RICH_TEXT", "Marca de texto inválida ou não permitida");
    }
}
