package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.user.dto.request.CreateUserRequest;
import com.scriptles.cabinet.user.dto.request.UpdateUserProfile;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().trim();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new RegistrationConflictException("username", "Este nome de usuário já está em uso");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RegistrationConflictException("email", "Este e-mail já está cadastrado");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User newUser = User.create(
                email,
                username,
                displayName,
                passwordHash
        );

        return userRepository.save(newUser);
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateUserProfile request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));

        String displayName = request.displayName().trim();
        if (displayName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "O nome de exibição é obrigatório");
        }

        user.setDisplayName(displayName);
        user.setBiography(normalizeOptional(request.biography()));
        user.setAvatarUlr(normalizeAvatarUrl(request.avatarUrl()));
        user.setProfileVisibility(request.profileVisibility());
        return userRepository.save(user);
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeAvatarUrl(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) return null;
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_URL",
                    "O avatar deve ser uma URL HTTPS válida");
        }
        return normalized;
    }
}
