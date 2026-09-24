package com.scriptles.cabinet.user.dto.response;

import java.util.List;

public record LibraryFilterOptionsResponse(List<GenreOption> genres) {
    public record GenreOption(String id, String name) {}
}
