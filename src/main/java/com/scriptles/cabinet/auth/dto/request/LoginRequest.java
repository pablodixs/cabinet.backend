package com.scriptles.cabinet.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Informe seu e-mail ou nome de usuário")
        String identifier,

        @NotBlank(message = "Informe sua senha")
        String password
) {
}
