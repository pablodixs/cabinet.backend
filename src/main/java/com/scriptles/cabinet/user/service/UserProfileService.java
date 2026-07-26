package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.service.MediaCreditService;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.dto.response.LibraryFilterOptionsResponse;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.ProfileStatsResponse;
import com.scriptles.cabinet.user.dto.response.ProfileLikeResponse;
import com.scriptles.cabinet.user.dto.response.ProfileTagResponse;
import com.scriptles.cabinet.user.dto.response.ProfileFavoriteResponse;
import com.scriptles.cabinet.user.dto.response.UserSearchResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.dto.response.UserSummaryResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.entity.UserProfileFavorite;
import com.scriptles.cabinet.user.entity.UserTag;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.LibraryRatingFilter;
import com.scriptles.cabinet.user.enums.LibrarySort;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.repository.UserProfileFavoriteRepository;
import com.scriptles.cabinet.user.repository.UserMediaTagRepository;
import com.scriptles.cabinet.user.repository.UserTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
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
    private final SocialAccessPolicy socialAccessPolicy;
    private final SocialGraphService socialGraphService;
    private final MediaLikeRepository mediaLikeRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final MediaCreditService mediaCreditService;
    private final UserProfileFavoriteRepository favoriteRepository;
    private final UserTagRepository tagRepository;
    private final UserMediaTagRepository mediaTagRepository;

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
    public PageResponse<UserSearchResponse> search(
            String query, UUID viewerId, int page, int size) {
        String normalizedQuery = normalizeSearchQuery(query);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("username"))
        );
        Page<User> users = userRepository.searchProfiles(
                normalizedQuery, viewerId, pageable);
        SocialGraphService.RelationshipBatch relationships = socialGraphService.relationships(
                viewerId, users.stream().map(User::getId).toList());
        return PageResponse.from(users.map(user -> {
            boolean privateProfile = isPrivateProfile(user);
            FollowState state = relationships.states().getOrDefault(user.getId(), FollowState.NONE);
            boolean accessible = !privateProfile || state == FollowState.FOLLOWING;
            return new UserSearchResponse(
                    user.getId(), user.getUsername(), user.getDisplayName(),
                    accessible ? user.getBiography() : null, user.getAvatarUlr(),
                    user.getAccountTier() == AccountTier.PRO,
                    privateProfile, accessible, state,
                    relationships.followingViewer().contains(user.getId())
            );
        }));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse findSummary(String username, UUID viewerId) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(this::profileNotFound);
        if (socialAccessPolicy.isBlocked(viewerId, user.getId())) throw profileNotFound();
        boolean ownProfile = viewerId.equals(user.getId());
        boolean accessible = socialAccessPolicy.canViewProfile(user, viewerId);
        SocialAccessPolicy.Relationship relationship = socialAccessPolicy.relationship(
                viewerId, user.getId());
        return new UserSummaryResponse(
                user.getId(), user.getUsername(), user.getDisplayName(),
                accessible ? user.getBiography() : null, user.getAvatarUlr(),
                user.getAccountTier() == AccountTier.PRO, ownProfile,
                isPrivateProfile(user), accessible, user.getFollowersCount(),
                user.getFollowingCount(), relationship.state(), relationship.followsViewer()
        );
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
                profileUser.getId(),
                recentEntries.stream().map(UserMedia::getMedia).toList()
        );
        List<LibraryMediaResponse> recentItems = recentEntries.stream()
                .map(entry -> LibraryMediaResponse.from(
                        entry,
                        referencesByMediaId.get(entry.getMedia().getId()),
                        artworks.get(entry.getMedia().getId()).coverUrl()
                ))
                .toList();
        List<UserProfileFavorite> favoriteEntries =
                favoriteRepository.findAllByUserIdOrderByPositionAsc(profileUser.getId());
        Map<UUID, UserArtworkResolver.ResolvedArtwork> favoriteArtworks = resolveArtwork(
                profileUser.getId(),
                favoriteEntries.stream().map(UserProfileFavorite::getMedia).toList()
        );
        List<ProfileFavoriteResponse> favoriteItems = favoriteEntries.stream()
                .map(entry -> ProfileFavoriteResponse.from(
                        entry,
                        favoriteArtworks.get(entry.getMedia().getId()).coverUrl()
                ))
                .toList();
        UserMediaRepository.ProfileStatisticsProjection statistics =
                userMediaRepository.findProfileStatistics(profileUser.getId(), ownProfile);

        SocialAccessPolicy.Relationship relationship = socialAccessPolicy == null
                ? new SocialAccessPolicy.Relationship(FollowState.NONE, false)
                : socialAccessPolicy.relationship(viewerId, profileUser.getId());

        return new UserProfileResponse(
                profileUser.getId(),
                profileUser.getUsername(),
                profileUser.getDisplayName(),
                profileUser.getBiography(),
                profileUser.getAvatarUlr(),
                profileUser.getAccountTier() == AccountTier.PRO,
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
                recentItems,
                favoriteItems,
                profileUser.getFollowersCount(),
                profileUser.getFollowingCount(),
                isPrivateProfile(profileUser),
                relationship.state(),
                relationship.followsViewer()
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
        Page<com.scriptles.cabinet.user.entity.UserMediaActivity> entries = access.followerAccess()
                ? userMediaActivityRepository.findProfileActivitiesVisibleToFollower(
                        access.user().getId(),
                        List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                        PageRequest.of(page, size))
                : userMediaActivityRepository.findProfileActivities(
                        access.user().getId(),
                        access.ownProfile(),
                        Visibility.PUBLIC,
                        PageRequest.of(page, size));
        Map<UUID, ExternalReference> referencesByMediaId = findActivityReferences(entries.getContent());
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                access.user().getId(),
                entries.getContent().stream().map(entry -> entry.getMedia()).toList()
        );
        List<UUID> mediaIds = entries.getContent().stream()
                .map(entry -> entry.getMedia().getId())
                .distinct()
                .toList();
        java.util.Set<UUID> likedMediaIds = mediaIds.isEmpty()
                ? java.util.Set.of()
                : java.util.Set.copyOf(mediaLikeRepository.findLikedMediaIds(
                        access.user().getId(), mediaIds));

        return PageResponse.from(entries.map(entry -> ProfileActivityResponse.from(
                entry,
                referencesByMediaId.get(entry.getMedia().getId()),
                artworks.get(entry.getMedia().getId()).coverUrl(),
                likedMediaIds.contains(entry.getMedia().getId())
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryMediaResponse> findLibrary(
            String username,
            UUID viewerId,
            UserMediaStatus status,
            MediaType type,
            String query,
            String genre,
            LibraryRatingFilter rating,
            LibrarySort sort,
            int page,
            int size
    ) {
        ProfileAccess access = findProfileAccess(username, viewerId);
        List<Visibility> visibleInteractions = visibleInteractions(access);
        Page<UserMedia> entries = userMediaRepository.findProfileLibrary(
                access.user().getId(),
                access.ownProfile(),
                status,
                type,
                normalizeLibraryQuery(query),
                normalizeBlank(genre),
                rating.name(),
                visibleInteractions,
                sort.name(),
                PageRequest.of(page, size)
        );
        Map<UUID, ExternalReference> referencesByMediaId = findReferences(entries);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                access.user().getId(),
                entries.getContent().stream().map(UserMedia::getMedia).toList()
        );
        var mediaItems = entries.getContent().stream().map(UserMedia::getMedia).toList();
        var mediaIds = mediaItems.stream().map(media -> media.getId()).toList();
        java.util.Set<UUID> likedMediaIds = mediaIds.isEmpty()
                ? java.util.Set.of()
                : java.util.Set.copyOf(mediaLikeRepository.findLikedMediaIds(
                        access.user().getId(), mediaIds));
        Map<UUID, java.math.BigDecimal> ratingsByMediaId = mediaIds.isEmpty()
                ? Map.of()
                : ratingRepository
                        .findAllByUserIdAndMediaIdInAndVisibilityIn(
                                access.user().getId(), mediaIds, visibleInteractions)
                        .stream()
                        .collect(Collectors.toMap(
                                item -> item.getMedia().getId(),
                                item -> item.getValue()
                        ));
        java.util.Set<UUID> reviewedMediaIds = mediaIds.isEmpty()
                ? java.util.Set.of()
                : java.util.Set.copyOf(reviewRepository.findVisibleReviewedMediaIds(
                        access.user().getId(), mediaIds, visibleInteractions));
        Map<UUID, MediaCreditService.CreditSummary> creditsByMediaId =
                mediaCreditService.summaries(mediaItems);

        return PageResponse.from(entries.map(entry -> LibraryMediaResponse.from(
                entry,
                referencesByMediaId.get(entry.getMedia().getId()),
                artworks.get(entry.getMedia().getId()).coverUrl(),
                likedMediaIds.contains(entry.getMedia().getId()),
                ratingsByMediaId.get(entry.getMedia().getId()),
                reviewedMediaIds.contains(entry.getMedia().getId()),
                creditsByMediaId
                        .getOrDefault(
                                entry.getMedia().getId(),
                                MediaCreditService.CreditSummary.empty())
                        .creator()
        )));
    }

    @Transactional(readOnly = true)
    public LibraryFilterOptionsResponse findLibraryFilters(
            String username,
            UUID viewerId
    ) {
        ProfileAccess access = findProfileAccess(username, viewerId);
        return new LibraryFilterOptionsResponse(
                userMediaRepository.findProfileLibraryGenres(
                        access.user().getId(), access.ownProfile())
        );
    }

    private List<Visibility> visibleInteractions(ProfileAccess access) {
        if (access.ownProfile()) {
            return List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.PRIVATE);
        }
        if (access.followerAccess()) {
            return List.of(Visibility.PUBLIC, Visibility.FOLLOWERS);
        }
        return List.of(Visibility.PUBLIC);
    }

    private String normalizeLibraryQuery(String query) {
        String normalized = normalizeBlank(query);
        return normalized == null
                ? null
                : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileLikeResponse> findLikes(
            String username,
            UUID viewerId,
            int page,
            int size
    ) {
        ProfileAccess access = findProfileAccess(username, viewerId);
        Page<MediaLike> likes = mediaLikeRepository.findByUserIdOrderByLikedAtDescIdDesc(
                access.user().getId(), PageRequest.of(page, size));
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                access.user().getId(),
                likes.getContent().stream().map(MediaLike::getMedia).toList()
        );

        return PageResponse.from(likes.map(like -> ProfileLikeResponse.from(
                like,
                artworks.get(like.getMedia().getId()).coverUrl()
        )));
    }

    @Transactional(readOnly = true)
    public List<ProfileTagResponse> findTags(
            String username,
            UUID viewerId,
            int limit
    ) {
        ProfileAccess access = findProfileAccess(username, viewerId);
        List<Visibility> visibilities = access.ownProfile()
                ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.PRIVATE)
                : access.followerAccess()
                    ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS)
                    : List.of(Visibility.PUBLIC);

        Map<String, TagSummary> summaries = new LinkedHashMap<>();
        userMediaActivityRepository.findProfileTags(
                        access.user().getId(), visibilities, PageRequest.of(0, 200))
                .forEach(tag -> summaries.compute(
                        normalizeTagName(tag.getName()),
                        (ignored, current) -> current == null
                                ? new TagSummary(tag.getName(), tag.getUsageCount())
                                : current.add(tag.getUsageCount())));

        Map<UUID, Long> mediaUsageByTag = mediaTagRepository
                .countUsageByUserId(access.user().getId())
                .stream()
                .collect(Collectors.toMap(
                        UserMediaTagRepository.TagUsageCount::getTagId,
                        UserMediaTagRepository.TagUsageCount::getUsageCount));
        for (UserTag tag : tagRepository.findAllByUserIdOrderByNameAsc(
                access.user().getId())) {
            long mediaUsage = mediaUsageByTag.getOrDefault(tag.getId(), 0L);
            summaries.compute(tag.getNormalizedName(), (ignored, current) ->
                    current == null
                            ? new TagSummary(tag.getName(), mediaUsage)
                            : new TagSummary(tag.getName(),
                                    current.usageCount() + mediaUsage));
        }

        return summaries.values().stream()
                .sorted(Comparator
                        .comparingLong(TagSummary::usageCount).reversed()
                        .thenComparing(TagSummary::name,
                                String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(tag -> new ProfileTagResponse(
                        tag.name(), tag.usageCount()))
                .toList();
    }

    private String normalizeTagName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private record TagSummary(String name, long usageCount) {
        private TagSummary add(long count) {
            return new TagSummary(name, usageCount + count);
        }
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

        boolean accessible = socialAccessPolicy == null
                ? ownProfile || profileUser.getProfileVisibility() == null
                    || profileUser.getProfileVisibility() == Visibility.PUBLIC
                : socialAccessPolicy.canViewProfile(profileUser, viewerId);
        if (!accessible) {
            throw profileNotFound();
        }

        boolean followerAccess = !ownProfile && viewerId != null
                && socialAccessPolicy != null
                && socialAccessPolicy.isAcceptedFollower(viewerId, profileUser.getId());
        return new ProfileAccess(profileUser, ownProfile, followerAccess);
    }

    private ApiException profileNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_PROFILE_NOT_FOUND",
                "Perfil não encontrado"
        );
    }

    private record ProfileAccess(User user, boolean ownProfile, boolean followerAccess) {
    }

    private String normalizeSearchQuery(String query) {
        String normalized = query.trim();
        if (normalized.startsWith("@")) normalized = normalized.substring(1).trim();
        return normalized;
    }

    private boolean isPrivateProfile(User user) {
        return user.getProfileVisibility() != null
                && user.getProfileVisibility() != Visibility.PUBLIC;
    }
}
