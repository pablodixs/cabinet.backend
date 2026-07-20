package com.scriptles.cabinet.security;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        String username,
        String displayName,
        String passwordHash,
        Collection<? extends GrantedAuthority> authorities,
        boolean active,
        UserRole role,
        AccountTier accountTier
) implements UserDetails {

    public AuthenticatedUser(
            UUID id,
            String email,
            String username,
            String displayName,
            String passwordHash,
            Collection<? extends GrantedAuthority> authorities,
            boolean active
    ) {
        this(id, email, username, displayName, passwordHash, authorities, active, UserRole.USER, AccountTier.FREE);
    }

    public AuthenticatedUser(
            UUID id,
            String email,
            String username,
            String displayName,
            String passwordHash,
            Collection<? extends GrantedAuthority> authorities,
            boolean active,
            UserRole role
    ) {
        this(id, email, username, displayName, passwordHash, authorities, active, role, AccountTier.FREE);
    }

    public static AuthenticatedUser from(User user) {
        return from(user, user.getRole() == null ? UserRole.USER : user.getRole());
    }

    public static AuthenticatedUser from(User user, UserRole role) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPasswordHash(),
                authorities(role),
                Boolean.TRUE.equals(user.getActive()),
                role,
                user.getAccountTier() == null ? AccountTier.FREE : user.getAccountTier()
        );
    }

    private static List<SimpleGrantedAuthority> authorities(UserRole role) {
        if (role == UserRole.ADMIN) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_MODERATOR"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }
        if (role == UserRole.MODERATOR) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_MODERATOR")
            );
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public boolean moderator() {
        return role.canModerate();
    }

    public boolean admin() {
        return role == UserRole.ADMIN;
    }

    public boolean pro() {
        return accountTier == AccountTier.PRO;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
