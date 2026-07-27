package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.UserSearchResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.repository.UserProfileFavoriteRepository;
import com.scriptles.cabinet.user.repository.UserMediaTagRepository;
import com.scriptles.cabinet.user.repository.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMediaRepository userMediaRepository;

    @Mock
    private UserMediaActivityRepository userMediaActivityRepository;

    @Mock
    private ExternalReferenceRepository externalReferenceRepository;

    @Mock
    private UserArtworkResolver userArtworkResolver;

    @Mock
    private MediaLikeRepository mediaLikeRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private UserProfileFavoriteRepository favoriteRepository;

    @Mock
    private UserTagRepository tagRepository;

    @Mock
    private UserMediaTagRepository mediaTagRepository;

    @Mock
    private MediaListRepository mediaListRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @BeforeEach
    void resolveCanonicalArtworkByDefault() {
        lenient().when(favoriteRepository.findAllByUserIdOrderByPositionAsc(
                any(UUID.class))).thenReturn(List.of());
        lenient().when(ratingRepository.ratingDistributionForUser(
                any(UUID.class),
                org.mockito.ArgumentMatchers.<Visibility>anyCollection()
        )).thenReturn(List.of());
        lenient().when(userArtworkResolver.resolve(
                any(UUID.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<Media>>any()
        )).thenAnswer(invocation -> {
            java.util.Collection<Media> mediaItems = invocation.getArgument(1);
            return mediaItems.stream().collect(java.util.stream.Collectors.toMap(
                    Media::getId,
                    media -> new UserArtworkResolver.ResolvedArtwork(
                            media.getCoverUrl(),
                            media.getBackdropUrl(),
                            false,
                            false
                    )
            ));
        });
    }

    @Test
    void searchesOnlyVisibleActiveProfilesByName() {
        User profileUser = user(Visibility.PUBLIC);
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("username"))
        );
        when(userRepository.searchVisibleProfiles("maria", Visibility.PUBLIC, pageable))
                .thenReturn(new PageImpl<>(List.of(profileUser), pageable, 1));

        UserSearchResponse result = userProfileService.search(" @maria ", 0, 20)
                .items()
                .getFirst();

        assertThat(result.username()).isEqualTo("maria");
        assertThat(result.displayName()).isEqualTo("Maria Cabinet");
        assertThat(result.avatarUrl()).isNull();
        verify(userRepository).searchVisibleProfiles("maria", Visibility.PUBLIC, pageable);
    }

    @Test
    void returnsOnlyPublicProfileDataToOtherUsers() {
        User profileUser = user(Visibility.PUBLIC);
        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaRepository.findRecentProfileLibrary(
                eq(profileUser.getId()),
                eq(false),
                any(Pageable.class)
        )).thenReturn(List.of());
        UserMediaRepository.ProfileStatisticsProjection statistics = statistics(
                12, 7, 2, 540, 930, 8, 3, 4, 2, 5
        );
        when(userMediaRepository.findProfileStatistics(profileUser.getId(), false))
                .thenReturn(statistics);
        RatingRepository.RatingDistributionProjection ratingBucket =
                mock(RatingRepository.RatingDistributionProjection.class);
        when(ratingBucket.getRating()).thenReturn(new java.math.BigDecimal("4.5"));
        when(ratingBucket.getRatingCount()).thenReturn(3L);
        when(ratingRepository.ratingDistributionForUser(
                profileUser.getId(),
                List.of(Visibility.PUBLIC)
        )).thenReturn(List.of(ratingBucket));

        UserProfileResponse response = userProfileService.findByUsername(
                " maria ",
                UUID.randomUUID()
        );

        assertThat(response.username()).isEqualTo("maria");
        assertThat(response.pro()).isFalse();
        assertThat(response.ownProfile()).isFalse();
        assertThat(response.libraryCount()).isEqualTo(12);
        assertThat(response.completedCount()).isEqualTo(7);
        assertThat(response.inProgressCount()).isEqualTo(2);
        assertThat(response.stats().watchedMinutes()).isEqualTo(540);
        assertThat(response.stats().pagesRead()).isEqualTo(930);
        assertThat(response.stats().episodesWatched()).isEqualTo(8);
        assertThat(response.stats().albumsConsumed()).isEqualTo(3);
        assertThat(response.stats().moviesConsumed()).isEqualTo(4);
        assertThat(response.stats().seriesConsumed()).isEqualTo(2);
        assertThat(response.stats().booksConsumed()).isEqualTo(5);
        assertThat(response.recentItems()).isEmpty();
        assertThat(response.ratings().total()).isEqualTo(3);
        assertThat(response.ratings().distribution()).singleElement()
                .satisfies(bucket -> {
                    assertThat(bucket.rating()).isEqualByComparingTo("4.5");
                    assertThat(bucket.count()).isEqualTo(3);
                });
    }

    @Test
    void includesPrivateEntriesWithoutExposingEmailForTheProfileOwner() {
        User profileUser = user(Visibility.PRIVATE);
        profileUser.setAccountTier(AccountTier.PRO);
        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaRepository.findRecentProfileLibrary(
                eq(profileUser.getId()),
                eq(true),
                any(Pageable.class)
        )).thenReturn(List.of());
        UserMediaRepository.ProfileStatisticsProjection statistics = statistics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );
        when(userMediaRepository.findProfileStatistics(profileUser.getId(), true))
                .thenReturn(statistics);

        UserProfileResponse response = userProfileService.findByUsername(
                "maria",
                profileUser.getId()
        );

        assertThat(response.ownProfile()).isTrue();
        assertThat(response.pro()).isTrue();
        verify(userMediaRepository).findProfileStatistics(profileUser.getId(), true);
    }

    @Test
    void usesProfileOwnersCustomCoverForRecentItems() {
        User profileUser = user(Visibility.PUBLIC);
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle("Central do Brasil");
        media.setCoverUrl("https://images.example/default.jpg");
        UserMedia entry = new UserMedia();
        entry.setId(UUID.randomUUID());
        entry.setUser(profileUser);
        entry.setMedia(media);
        entry.setStatus(UserMediaStatus.COMPLETED);

        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaRepository.findRecentProfileLibrary(
                eq(profileUser.getId()), eq(false), any(Pageable.class)))
                .thenReturn(List.of(entry));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                List.of(media.getId()))).thenReturn(List.of());
        when(userArtworkResolver.resolve(profileUser.getId(), List.of(media)))
                .thenReturn(Map.of(
                        media.getId(),
                        new UserArtworkResolver.ResolvedArtwork(
                                "https://images.example/custom.jpg",
                                null,
                                true,
                                false
                        )
                ));
        UserMediaRepository.ProfileStatisticsProjection profileStatistics =
                statistics(1, 1, 0, 0, 0, 0, 0, 1, 0, 0);
        when(userMediaRepository.findProfileStatistics(profileUser.getId(), false))
                .thenReturn(profileStatistics);

        UserProfileResponse response = userProfileService.findByUsername(
                "maria", UUID.randomUUID());

        assertThat(response.recentItems().getFirst().coverUrl())
                .isEqualTo("https://images.example/custom.jpg");
        verify(userArtworkResolver).resolve(profileUser.getId(), List.of(media));
    }

    @Test
    void returnsPublicProfileActivitiesWithMediaLinks() {
        User profileUser = user(Visibility.PUBLIC);
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.BOOK);
        media.setTitle("Torto Arado");
        media.setCoverUrl("https://images.example/torto-arado.jpg");
        media.setReleaseDate(LocalDate.of(2019, 1, 1));

        Instant occurredAt = Instant.parse("2026-07-14T12:00:00Z");
        UserMediaActivity entry = new UserMediaActivity();
        entry.setId(UUID.randomUUID());
        entry.setUser(profileUser);
        entry.setMedia(media);
        entry.setType(ProfileActivityType.COMPLETED);
        entry.setOccurredOn(LocalDate.of(2026, 7, 14));
        entry.setVisibility(Visibility.PUBLIC);
        entry.setRating(new java.math.BigDecimal("4.5"));
        entry.setReviewContent("Uma ótima leitura.");

        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.GOOGLE_BOOKS);
        reference.setExternalId("book-123");
        reference.setPrimaryReference(true);

        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaActivityRepository.findProfileActivities(
                eq(profileUser.getId()),
                eq(false),
                eq(Visibility.PUBLIC),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(entry)));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                List.of(media.getId())
        )).thenReturn(List.of(reference));
        when(mediaLikeRepository.findLikedMediaIds(
                profileUser.getId(), List.of(media.getId())))
                .thenReturn(List.of(media.getId()));

        ProfileActivityResponse activity = userProfileService.findActivities(
                "maria",
                UUID.randomUUID(),
                0,
                20
        ).items().getFirst();

        assertThat(activity.type()).isEqualTo(ProfileActivityType.COMPLETED);
        assertThat(activity.occurredOn()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(activity.title()).isEqualTo("Torto Arado");
        assertThat(activity.source()).isEqualTo(ExternalSource.GOOGLE_BOOKS);
        assertThat(activity.externalId()).isEqualTo("book-123");
        assertThat(activity.rating()).isEqualByComparingTo("4.5");
        assertThat(activity.liked()).isTrue();
        assertThat(activity.hasReview()).isTrue();
    }

    @Test
    void hidesPrivateProfilesFromOtherUsers() {
        User profileUser = user(Visibility.PRIVATE);
        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));

        assertThatThrownBy(() -> userProfileService.findByUsername(
                "maria",
                UUID.randomUUID()
        ))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("USER_PROFILE_NOT_FOUND");
                });

        verify(userMediaRepository, never()).findRecentProfileLibrary(
                any(UUID.class),
                anyBoolean(),
                any(Pageable.class)
        );
    }

    @Test
    void returnsDistinctTaggedMediaFromDirectTagsAndVisibleActivities() {
        User profileUser = user(Visibility.PUBLIC);
        Media direct = new Media();
        direct.setId(UUID.randomUUID());
        direct.setType(MediaType.MOVIE);
        direct.setTitle("Bacurau");
        Media activity = new Media();
        activity.setId(UUID.randomUUID());
        activity.setType(MediaType.BOOK);
        activity.setTitle("Arrival");
        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(mediaTagRepository.findTaggedMedia(
                profileUser.getId(), "favoritos"))
                .thenReturn(List.of(direct));
        when(userMediaActivityRepository.findMediaByTag(
                profileUser.getId(), List.of(Visibility.PUBLIC), "favoritos"))
                .thenReturn(List.of(activity, direct));

        var response = userProfileService.findTaggedMedia(
                "maria", UUID.randomUUID(), "#Favoritos");

        assertThat(response)
                .extracting(item -> item.title())
                .containsExactly("Arrival", "Bacurau");
    }

    @Test
    void returnsVisibleActivitiesForUserAndMedia() {
        User profileUser = user(Visibility.PUBLIC);
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle("Bacurau");
        UserMediaActivity entry = new UserMediaActivity();
        entry.setId(UUID.randomUUID());
        entry.setUser(profileUser);
        entry.setMedia(media);
        entry.setType(ProfileActivityType.WATCHED);
        entry.setOccurredOn(LocalDate.of(2026, 7, 20));
        entry.setVisibility(Visibility.PUBLIC);

        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaActivityRepository.findByUserAndMediaVisible(
                profileUser.getId(),
                media.getId(),
                List.of(Visibility.PUBLIC)
        )).thenReturn(List.of(entry));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                List.of(media.getId())
        )).thenReturn(List.of());
        when(mediaLikeRepository.existsByUserIdAndMediaId(
                profileUser.getId(), media.getId()
        )).thenReturn(true);

        List<ProfileActivityResponse> response =
                userProfileService.findActivitiesByMedia(
                        "maria", media.getId(), UUID.randomUUID());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().mediaId()).isEqualTo(media.getId());
        assertThat(response.getFirst().liked()).isTrue();
    }

    private User user(Visibility visibility) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("maria");
        user.setDisplayName("Maria Cabinet");
        user.setEmail("maria@example.com");
        user.setActive(true);
        user.setProfileVisibility(visibility);
        return user;
    }

    private UserMediaRepository.ProfileStatisticsProjection statistics(
            long libraryCount,
            long completedCount,
            long inProgressCount,
            long watchedMinutes,
            long pagesRead,
            long episodesWatched,
            long albumsConsumed,
            long moviesConsumed,
            long seriesConsumed,
            long booksConsumed
    ) {
        UserMediaRepository.ProfileStatisticsProjection statistics =
                mock(UserMediaRepository.ProfileStatisticsProjection.class);
        when(statistics.getLibraryCount()).thenReturn(libraryCount);
        when(statistics.getCompletedCount()).thenReturn(completedCount);
        when(statistics.getInProgressCount()).thenReturn(inProgressCount);
        when(statistics.getWatchedMinutes()).thenReturn(watchedMinutes);
        when(statistics.getPagesRead()).thenReturn(pagesRead);
        when(statistics.getEpisodesWatched()).thenReturn(episodesWatched);
        when(statistics.getAlbumsConsumed()).thenReturn(albumsConsumed);
        when(statistics.getMoviesConsumed()).thenReturn(moviesConsumed);
        when(statistics.getSeriesConsumed()).thenReturn(seriesConsumed);
        when(statistics.getBooksConsumed()).thenReturn(booksConsumed);
        return statistics;
    }
}
