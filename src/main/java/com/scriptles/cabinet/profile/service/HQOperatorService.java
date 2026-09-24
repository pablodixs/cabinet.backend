package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.profile.entity.HQOperator;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQOperatorRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class HQOperatorService {
    private final HQOperatorRepository operators;
    private final HQProfileRepository hqs;
    private final PasswordEncoder passwords;

    public record OperatorInput(String email, String displayName, String password, HQMemberRole role) {}
    public record OperatorView(UUID id, String email, String displayName, HQMemberRole role, boolean active) {}

    @Transactional(readOnly = true)
    public AuthenticatedHQ authenticate(String handle, String email, String password) {
        HQProfile hq = hqs.findByProfileHandleIgnoreCase(handle).orElseThrow(this::invalidCredentials);
        HQOperator operator = operators.findByHqProfileIdAndEmailIgnoreCase(hq.getId(), email).orElseThrow(this::invalidCredentials);
        if (!operator.isActive() || hq.getClaimStatus() == HQClaimStatus.SUSPENDED || !passwords.matches(password, operator.getPasswordHash())) throw invalidCredentials();
        return principal(operator);
    }

    @Transactional(readOnly = true)
    public AuthenticatedHQ refresh(UUID operatorId) {
        HQOperator operator = operators.findById(operatorId).orElseThrow(this::invalidCredentials);
        if (!operator.isActive() || operator.getHqProfile().getClaimStatus() == HQClaimStatus.SUSPENDED) throw invalidCredentials();
        return principal(operator);
    }

    @Transactional
    public OperatorView create(UUID profileId, OperatorInput input) {
        HQProfile hq = hqs.findByProfileId(profileId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "HQ não encontrada"));
        if (input.email() == null || input.email().isBlank() || input.displayName() == null || input.displayName().isBlank() || input.password() == null || input.password().length() < 12 || input.role() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPERATOR", "Informe e-mail, nome, papel e senha de pelo menos 12 caracteres");
        }
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (operators.findByHqProfileIdAndEmailIgnoreCase(hq.getId(), email).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "OPERATOR_EXISTS", "Este e-mail já tem acesso à HQ");
        HQOperator operator = new HQOperator();
        operator.setHqProfile(hq); operator.setEmail(email); operator.setDisplayName(input.displayName().trim());
        operator.setPasswordHash(passwords.encode(input.password())); operator.setRole(input.role());
        return view(operators.save(operator));
    }

    @Transactional(readOnly = true)
    public List<OperatorView> list(UUID hqId) {
        return operators.findByHqProfileIdOrderByCreatedAtAsc(hqId).stream().map(this::view).toList();
    }

    @Transactional
    public void deactivate(UUID hqId, UUID operatorId) {
        HQOperator operator = operators.findById(operatorId).filter(o -> o.getHqProfile().getId().equals(hqId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OPERATOR_NOT_FOUND", "Acesso não encontrado"));
        if (operator.getRole() == HQMemberRole.OWNER && operators.findByHqProfileIdOrderByCreatedAtAsc(hqId).stream()
                .filter(o -> o.isActive() && o.getRole() == HQMemberRole.OWNER).count() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER", "A HQ precisa manter um responsável ativo");
        }
        operator.setActive(false);
    }

    @Transactional
    public void changePassword(UUID operatorId, String currentPassword, String newPassword) {
        HQOperator operator = operators.findById(operatorId).orElseThrow(this::invalidCredentials);
        if (!operator.isActive() || currentPassword == null || !passwords.matches(currentPassword, operator.getPasswordHash())) throw invalidCredentials();
        if (newPassword == null || newPassword.length() < 12) throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "A nova senha deve ter pelo menos 12 caracteres");
        operator.setPasswordHash(passwords.encode(newPassword));
    }

    private AuthenticatedHQ principal(HQOperator operator) {
        return new AuthenticatedHQ(operator.getId(), operator.getHqProfile().getId(), operator.getHqProfile().getProfile().getId(),
                operator.getEmail(), operator.getDisplayName(), operator.getRole(), operator.isActive());
    }
    private OperatorView view(HQOperator operator) { return new OperatorView(operator.getId(), operator.getEmail(), operator.getDisplayName(), operator.getRole(), operator.isActive()); }
    private ApiException invalidCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "HQ_INVALID_CREDENTIALS", "Credenciais da HQ inválidas"); }
}
