package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.PostType; import jakarta.validation.constraints.*;
public record PostRequest(@NotNull PostType type,@Size(max=200) String title,@NotBlank @Size(max=20000) String body,@Size(max=500) String linkUrl,boolean publish) {}
