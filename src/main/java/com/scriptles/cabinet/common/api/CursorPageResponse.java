package com.scriptles.cabinet.common.api;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {
}
