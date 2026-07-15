package com.scriptles.cabinet.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Informe um nome de usuário")
        @Size(min = 3, max = 30, message = "O usuário deve ter entre 3 e 30 caracteres")
        @Pattern(
                regexp = "^[a-zA-Z0-9._]+$",
                message = "Use apenas letras, números, pontos e underscores"
        )
        String username,

        @NotBlank(message = "Informe seu nome")
        @Size(min = 3, max = 80, message = "O nome deve ter entre 3 e 80 caracteres")
        String displayName,

        @NotBlank(message = "Informe seu e-mail")
        @Email(message = "Informe um e-mail válido")
        String email,

        @NotBlank(message = "Informe uma senha")
        @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres")
        String password
        ) {
}
