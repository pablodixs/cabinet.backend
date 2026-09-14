package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record HQMemberRequest(@NotNull UUID accountId, @NotNull HQMemberRole role) {}
