package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.user.dto.request.CreateUserRequest;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

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
}
