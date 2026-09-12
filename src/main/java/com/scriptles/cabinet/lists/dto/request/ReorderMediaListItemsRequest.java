package com.scriptles.cabinet.lists.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ReorderMediaListItemsRequest(
        @NotEmpty List<@Valid UUID> itemIds
) {}
