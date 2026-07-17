package com.scriptles.cabinet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityAuthorization {
    private final CustomUserDetailsService userDetailsService;

    public boolean isModerator(Authentication authentication) {
        AuthenticatedUser user = current(authentication);
        return user != null && user.isEnabled() && user.moderator();
    }

    public boolean isAdmin(Authentication authentication) {
        AuthenticatedUser user = current(authentication);
        return user != null && user.isEnabled() && user.admin();
    }

    private AuthenticatedUser current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return null;
        }
        return userDetailsService.loadUserById(principal.id());
    }
}
