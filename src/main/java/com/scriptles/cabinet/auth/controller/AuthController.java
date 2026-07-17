package com.scriptles.cabinet.auth.controller;

import com.scriptles.cabinet.auth.dto.request.LoginRequest;
import com.scriptles.cabinet.auth.dto.response.AuthUserResponse;
import com.scriptles.cabinet.auth.dto.response.CsrfTokenResponse;
import com.scriptles.cabinet.auth.service.AuthService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.CustomUserDetailsService;
import com.scriptles.cabinet.user.dto.request.CreateUserRequest;
import com.scriptles.cabinet.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping("/login")
    public ResponseEntity<AuthUserResponse> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthenticatedUser user = authService.login(request, httpRequest, httpResponse);

        return ResponseEntity.ok(AuthUserResponse.from(user));
    }

    @PostMapping({"/register", "/create"})
    public ResponseEntity<AuthUserResponse> createUser(
            @RequestBody @Valid CreateUserRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        userService.createUser(request);
        AuthenticatedUser user = authService.login(
                new LoginRequest(request.email(), request.password()),
                httpRequest,
                httpResponse
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthUserResponse.from(user));
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return AuthUserResponse.from(userDetailsService.loadUserById(user.id()));
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrfToken(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
