package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.AlbumReleaseVersion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
public class AlbumMediaPageCursorCodec {
    public TrackPosition decodeTrack(String cursor) {
        String[] parts = decode(cursor, "t", 4);
        try {
            return new TrackPosition(parseNullableInt(parts[1]), parseNullableInt(parts[2]), UUID.fromString(parts[3]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    public VersionPosition decodeVersion(String cursor) {
        String[] parts = decode(cursor, "v", 2);
        try {
            return new VersionPosition(UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    public String encode(AlbumTrack track) {
        return encode("t|" + nullable(track.getDiscNumber()) + "|" + nullable(track.getTrackNumber())
                + "|" + track.getId());
    }

    public String encode(AlbumReleaseVersion version) {
        return encode("v|" + version.getId());
    }

    private String[] decode(String cursor, String expectedType, int expectedParts) {
        if (cursor == null || cursor.isBlank()) throw invalidCursor();
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != expectedParts || !expectedType.equals(parts[0])) throw invalidCursor();
            return parts;
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) throw apiException;
            throw invalidCursor();
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Integer parseNullableInt(String value) {
        return "-".equals(value) ? null : Integer.valueOf(value);
    }

    private String nullable(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private ApiException invalidCursor() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "O cursor informado é inválido");
    }

    public record TrackPosition(Integer discNumber, Integer trackNumber, UUID id) {
    }

    public record VersionPosition(UUID id) {
    }
}
