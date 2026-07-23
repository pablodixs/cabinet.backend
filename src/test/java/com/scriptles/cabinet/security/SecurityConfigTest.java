package com.scriptles.cabinet.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void configuresCsrfCookieForCrossSiteAuthentication() {
        CookieCsrfTokenRepository repository =
                securityConfig.csrfTokenRepository("none", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("none");
        assertThat(cookie.getSecure()).isTrue();
    }

    @Test
    void keepsLocalCsrfCookieCompatibleWithHttp() {
        CookieCsrfTokenRepository repository =
                securityConfig.csrfTokenRepository("lax", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("lax");
        assertThat(cookie.getSecure()).isFalse();
    }
}
