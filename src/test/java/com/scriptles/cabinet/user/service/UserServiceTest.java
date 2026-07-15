package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.user.dto.request.CreateUserRequest;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createsNormalizedUserWithEncodedPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "  minha.conta  ",
                "  Maria Cabinet  ",
                "  MARIA@EXAMPLE.COM  ",
                "segredo123"
        );
        when(passwordEncoder.encode("segredo123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(created).isSameAs(captor.getValue());
        assertThat(created.getUsername()).isEqualTo("minha.conta");
        assertThat(created.getDisplayName()).isEqualTo("Maria Cabinet");
        assertThat(created.getEmail()).isEqualTo("maria@example.com");
        assertThat(created.getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void rejectsDuplicatedUsernameBeforeEncodingPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "usuario",
                "Usuário Cabinet",
                "usuario@example.com",
                "segredo123"
        );
        when(userRepository.existsByUsernameIgnoreCase("usuario")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessage("Este nome de usuário já está em uso");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }
}
