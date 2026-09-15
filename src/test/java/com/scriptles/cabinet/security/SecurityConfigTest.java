package com.scriptles.cabinet.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void storesCsrfTokenInSessionForCrossSiteAuthentication() {
        CsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        assertThat(repository.loadToken(request).getToken()).isEqualTo(token.getToken());
        assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
        assertThat(response.getCookies()).isEmpty();
    }
}
