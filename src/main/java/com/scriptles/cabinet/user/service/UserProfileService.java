package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.UserSearchResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private static final int RECENT_ITEMS_LIMIT = 4;

    private final UserRepository userRepository;
    private final UserMediaRepository userMediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserSearchResponse> search(String query, int page, int size) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.startsWith("@")) {
            normalizedQuery = normalizedQuery.substring(1).trim();
        }
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("username"))
        );
        Page<User> users = userRepository.searchVisibleProfiles(
                normalizedQuery,
                Visibility.PUBLIC,
                pageable
        );
        return PageResponse.from(users.map(UserSearchResponse::from));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findByUsername(String username, UUID viewerId) {
        ProfileAccess access = findProfileAccess(username, viewerId);
        User profileUser = access.user();
        boolean ownProfile = access.ownProfile();

        PageRequest recentItemsPage = PageRequest.of(
                0,
                RECENT_ITEMS_LIMIT,
                Sort.by(Sort.Direction.DESC, "lastInteractionAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Page<UserMedia> recentEntries = userMediaRepository.findProfileLibrary(
                profileUser.getId(),
                ownProfile,
                recentItemsPage
        );
        Map<UUID, ExternalReference> referencesByMediaId = findReferences(recentEntries);
        List<LibraryMediaResponse> recentItems = recentEntries.getContent().stream()
                .map(entry -> LibraryMediaResponse.from(
                        entry,
                        referencesByMediaId.get(entry.getMedia().getId())
                ))
                .toList();

        return new UserProfileResponse(
                profileUser.getId(),
                profileUser.getUsername(),
                profileUser.getDisplayName(),
                profileUser.getBiography(),
                profileUser.getAvatarUlr(),
                ownProfile ? profileUser.getEmail() : null,
                ownProfile,
                userMediaRepository.countProfileLibrary(profileUser.getId(), null, ownProfile),
                userMediaRepository.countProfileLibrary(
                        profileUser.getId(),
                        UserMediaStatus.COMPLETED,
                        ownProfile
                ),
                userMediaRepository.countProfileLibrary(
                        profileUser.getId(),
                        UserMediaStatus.IN_PROGRESS,
                        ownProfile
                ),
                recentItems
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileActivityResponse> findActivities(
            String username,
            UUID viewerId,
            int page,
            int size
    ) {
        ProfileAccess access = findProfileAccess(username, viewerId);
        Page<UserMedia> entries = userMediaRepository.findProfileActivities(
                access.user().getId(),
                access.ownProfile(),
                PageRequest.of(page, size)
        );
        Map<UUID, ExternalReference> referencesByMediaId = findReferences(entries.getContent());

        return PageResponse.from(entries.map(entry -> ProfileActivityResponse.from(
                entry,
                referencesByMediaId.get(entry.getMedia().getId())
        )));
    }

    private Map<UUID, ExternalReference> findReferences(Page<UserMedia> entries) {
        return findReferences(entries.getContent());
    }

    private Map<UUID, ExternalReference> findReferences(List<UserMedia> entries) {
        if (entries.isEmpty()) {
            return Map.of();
        }

        return externalReferenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(
                        entries.stream()
                                .map(entry -> entry.getMedia().getId())
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        reference -> reference.getMedia().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private ProfileAccess findProfileAccess(String username, UUID viewerId) {
        User profileUser = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(this::profileNotFound);
        boolean ownProfile = viewerId != null && viewerId.equals(profileUser.getId());

        if (!ownProfile
                && profileUser.getProfileVisibility() != null
                && profileUser.getProfileVisibility() != Visibility.PUBLIC) {
            throw profileNotFound();
        }

        return new ProfileAccess(profileUser, ownProfile);
    }

    private ApiException profileNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_PROFILE_NOT_FOUND",
                "Perfil não encontrado"
        );
    }

    private record ProfileAccess(User user, boolean ownProfile) {
    }
}
