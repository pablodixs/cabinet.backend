package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.ProfileStatsResponse;
import com.scriptles.cabinet.user.dto.response.UserSearchResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
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
    private final UserMediaActivityRepository userMediaActivityRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final UserArtworkResolver userArtworkResolver;

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
        List<UserMedia> recentEntries = userMediaRepository.findRecentProfileLibrary(
                profileUser.getId(),
                ownProfile,
                recentItemsPage
        );
        Map<UUID, ExternalReference> referencesByMediaId = findReferences(recentEntries);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                viewerId,
                recentEntries.stream().map(UserMedia::getMedia).toList()
        );
        List<LibraryMediaResponse> recentItems = recentEntries.stream()
                .map(entry -> LibraryMediaResponse.from(
                        entry,
                        referencesByMediaId.get(entry.getMedia().getId()),
                        artworks.get(entry.getMedia().getId()).coverUrl()
                ))
                .toList();
        UserMediaRepository.ProfileStatisticsProjection statistics =
                userMediaRepository.findProfileStatistics(profileUser.getId(), ownProfile);

        return new UserProfileResponse(
                profileUser.getId(),
                profileUser.getUsername(),
                profileUser.getDisplayName(),
                profileUser.getBiography(),
                profileUser.getAvatarUlr(),
                ownProfile ? profileUser.getEmail() : null,
                ownProfile,
                statistics.getLibraryCount(),
                statistics.getCompletedCount(),
                statistics.getInProgressCount(),
                new ProfileStatsResponse(
                        statistics.getWatchedMinutes(),
                        statistics.getPagesRead(),
                        statistics.getEpisodesWatched(),
                        statistics.getAlbumsConsumed(),
                        statistics.getMoviesConsumed(),
                        statistics.getSeriesConsumed(),
                        statistics.getBooksConsumed()
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
        Page<com.scriptles.cabinet.user.entity.UserMediaActivity> entries = userMediaActivityRepository.findProfileActivities(
                access.user().getId(),
                access.ownProfile(),
                Visibility.PUBLIC,
                PageRequest.of(page, size)
        );
        Map<UUID, ExternalReference> referencesByMediaId = findActivityReferences(entries.getContent());
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                viewerId,
                entries.getContent().stream().map(entry -> entry.getMedia()).toList()
        );

        return PageResponse.from(entries.map(entry -> ProfileActivityResponse.from(
                entry,
                referencesByMediaId.get(entry.getMedia().getId()),
                artworks.get(entry.getMedia().getId()).coverUrl()
        )));
    }

    private Map<UUID, ExternalReference> findActivityReferences(
            List<com.scriptles.cabinet.user.entity.UserMediaActivity> entries
    ) {
        if (entries.isEmpty()) return Map.of();
        return externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                        entries.stream().map(entry -> entry.getMedia().getId()).toList())
                .stream()
                .collect(Collectors.toMap(
                        reference -> reference.getMedia().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private Map<UUID, UserArtworkResolver.ResolvedArtwork> resolveArtwork(
            UUID viewerId,
            java.util.Collection<com.scriptles.cabinet.media.entity.Media> mediaItems
    ) {
        if (userArtworkResolver != null) return userArtworkResolver.resolve(viewerId, mediaItems);
        return mediaItems.stream().collect(Collectors.toMap(
                com.scriptles.cabinet.media.entity.Media::getId,
                media -> new UserArtworkResolver.ResolvedArtwork(
                        media.getCoverUrl(), media.getBackdropUrl(), false, false)
        ));
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
