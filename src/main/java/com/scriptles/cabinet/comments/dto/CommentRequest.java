package com.scriptles.cabinet.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommentRequest(
        @NotBlank @Size(max = 2000) String content,
        UUID parentId
) {
}
