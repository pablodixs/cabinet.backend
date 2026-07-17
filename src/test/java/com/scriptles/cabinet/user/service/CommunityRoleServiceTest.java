package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.security.CustomUserDetailsService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserRoleChange;
import com.scriptles.cabinet.user.enums.UserRole;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.repository.UserRoleChangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityRoleServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserRoleChangeRepository roleChangeRepository;
    @Mock CustomUserDetailsService userDetailsService;

    @Test
    void promotesMemberAndRecordsAuditTrail() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User actor = user(actorId, UserRole.ADMIN);
        User target = user(targetId, UserRole.USER);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userDetailsService.effectiveRole(target)).thenReturn(UserRole.MODERATOR);

        CommunityRoleService service = new CommunityRoleService(
                userRepository, roleChangeRepository, userDetailsService);
        var response = service.updateRole(targetId, UserRole.MODERATOR, actorId);

        assertThat(target.getRole()).isEqualTo(UserRole.MODERATOR);
        assertThat(response.role()).isEqualTo(UserRole.MODERATOR);
        verify(userRepository).save(target);
        ArgumentCaptor<UserRoleChange> audit = ArgumentCaptor.forClass(UserRoleChange.class);
        verify(roleChangeRepository).save(audit.capture());
        assertThat(audit.getValue().getPreviousRole()).isEqualTo(UserRole.USER);
        assertThat(audit.getValue().getNewRole()).isEqualTo(UserRole.MODERATOR);
        assertThat(audit.getValue().getChangedBy()).isSameAs(actor);
    }

    @Test
    void preventsAdministratorFromChangingOwnRole() {
        UUID userId = UUID.randomUUID();
        CommunityRoleService service = new CommunityRoleService(
                userRepository, roleChangeRepository, userDetailsService);

        assertThatThrownBy(() -> service.updateRole(userId, UserRole.USER, userId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Peça para outro administrador alterar o seu papel");
        verifyNoInteractions(userRepository, roleChangeRepository);
    }

    private User user(UUID id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setDisplayName("User");
        user.setEmail(id + "@example.com");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
