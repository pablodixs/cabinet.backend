package com.scriptles.cabinet.auth.service;

import com.scriptles.cabinet.auth.dto.request.LoginRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;

    public AuthService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public void login(LoginRequest request) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        request.identifier().trim(),
                        request.password()
                );

        authenticationManager.authenticate(authentication);
    }
}