package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.security.CustomUserDetailsService;
import com.scriptles.cabinet.user.dto.response.CommunityUserRoleResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserRoleChange;
import com.scriptles.cabinet.user.entity.UserAccountTierChange;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.UserRole;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.repository.UserRoleChangeRepository;
import com.scriptles.cabinet.user.repository.UserAccountTierChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityRoleService {
    private final UserRepository userRepository;
    private final UserRoleChangeRepository userRoleChangeRepository;
    private final UserAccountTierChangeRepository userAccountTierChangeRepository;
    private final CustomUserDetailsService userDetailsService;

    @Transactional(readOnly = true)
    public PageResponse<CommunityUserRoleResponse> findUsers(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("displayName"), Sort.Order.asc("username")));
        String normalizedQuery = query == null ? "" : query.trim();
        Page<User> users = normalizedQuery.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        normalizedQuery, normalizedQuery, normalizedQuery, pageable);
        return PageResponse.from(users.map(user -> CommunityUserRoleResponse.from(
                user, userDetailsService.effectiveRole(user))));
    }

    @Transactional
    public CommunityUserRoleResponse updateRole(UUID targetUserId, UserRole newRole, UUID actorUserId) {
        if (targetUserId.equals(actorUserId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_CHANGE_OWN_ROLE",
                    "Peça para outro administrador alterar o seu papel"
            );
        }

        User target = requireUser(targetUserId);
        User actor = requireUser(actorUserId);
        UserRole previousRole = target.getRole() == null ? UserRole.USER : target.getRole();

        if (previousRole != newRole) {
            target.setRole(newRole);
            userRepository.save(target);

            UserRoleChange change = new UserRoleChange();
            change.setTargetUser(target);
            change.setChangedBy(actor);
            change.setPreviousRole(previousRole);
            change.setNewRole(newRole);
            userRoleChangeRepository.save(change);
        }

        return CommunityUserRoleResponse.from(target, userDetailsService.effectiveRole(target));
    }

    @Transactional
    public CommunityUserRoleResponse updateAccountTier(
            UUID targetUserId,
            AccountTier newTier,
            UUID actorUserId
    ) {
        User target = requireUser(targetUserId);
        User actor = requireUser(actorUserId);
        AccountTier previousTier = target.getAccountTier() == null ? AccountTier.FREE : target.getAccountTier();

        if (previousTier != newTier) {
            target.setAccountTier(newTier);
            userRepository.save(target);

            UserAccountTierChange change = new UserAccountTierChange();
            change.setTargetUser(target);
            change.setChangedBy(actor);
            change.setPreviousTier(previousTier);
            change.setNewTier(newTier);
            userAccountTierChangeRepository.save(change);
        }

        return CommunityUserRoleResponse.from(target, userDetailsService.effectiveRole(target));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
    }
}
