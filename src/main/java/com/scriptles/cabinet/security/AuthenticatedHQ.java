package com.scriptles.cabinet.security;

import com.scriptles.cabinet.profile.enums.HQMemberRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AuthenticatedHQ(UUID operatorId, UUID hqId, UUID profileId, String email,
                              String displayName, HQMemberRole role, boolean active)
        implements UserDetails {
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_HQ"));
    }
    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
}
