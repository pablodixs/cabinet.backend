package com.scriptles.cabinet.security;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.enums.UserRole;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Set<String> adminEmails;

    public CustomUserDetailsService(
            UserRepository userRepository,
            @Value("${app.moderation.admin-emails:}") String adminEmails
    ) {
        this.userRepository = userRepository;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public UserDetails loadUserByUsername(String identifier)
            throws UsernameNotFoundException {

        User user = userRepository
                .findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials")
                );

        return principal(user);
    }

    public AuthenticatedUser loadUserById(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return principal(user);
    }

    private AuthenticatedUser principal(User user) {
        return AuthenticatedUser.from(user, effectiveRole(user));
    }

    public UserRole effectiveRole(User user) {
        UserRole persistedRole = user.getRole() == null ? UserRole.USER : user.getRole();
        return adminEmails.contains(user.getEmail().toLowerCase(Locale.ROOT))
                ? UserRole.ADMIN : persistedRole;
    }
}
