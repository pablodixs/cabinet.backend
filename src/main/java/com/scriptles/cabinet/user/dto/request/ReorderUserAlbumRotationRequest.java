package com.scriptles.cabinet.user.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderUserAlbumRotationRequest(@NotNull List<@NotNull UUID> mediaIds) {}
