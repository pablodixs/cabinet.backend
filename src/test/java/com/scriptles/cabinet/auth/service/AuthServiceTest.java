package com.scriptles.cabinet.auth.service;

import com.scriptles.cabinet.auth.dto.request.LoginRequest;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAndPersistsSecurityContext() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                "maria@example.com",
                "maria",
                "Maria",
                "encoded-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true
        );
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated);

        AuthenticatedUser result = authService.login(
                new LoginRequest("  maria  ", "segredo123"),
                httpRequest,
                httpResponse
        );

        ArgumentCaptor<Authentication> authenticationCaptor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue().getName()).isEqualTo("maria");
        assertThat(result).isSameAs(principal);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(authenticated);

        ArgumentCaptor<SecurityContext> contextCaptor =
                ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(
                contextCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(httpRequest),
                org.mockito.ArgumentMatchers.eq(httpResponse)
        );
        assertThat(contextCaptor.getValue().getAuthentication()).isSameAs(authenticated);
    }
}
