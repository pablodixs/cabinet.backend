package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaRankingServiceTest {
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private MediaLikeRepository mediaLikeRepository;
    @Mock
    private UserMediaRepository userMediaRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MediaSearchItemAssembler mediaSearchItemAssembler;

    @InjectMocks
    private MediaRankingService mediaRankingService;

    @Test
    void returnsTopRatedInRepositoryOrder() {
        Media first = media("Primeira");
        Media second = media("Segunda");
        RatingRepository.RatedMediaProjection firstProjection = rated(first);
        RatingRepository.RatedMediaProjection secondProjection = rated(second);
        PageRequest pageable = PageRequest.of(1, 2);
        when(ratingRepository.findTopRatedMedia(Set.of("MOVIE"),
                com.scriptles.cabinet.user.enums.Visibility.PUBLIC, pageable))
                .thenReturn(new PageImpl<>(List.of(firstProjection, secondProjection), pageable, 5));
        when(mediaSearchItemAssembler.fromImported(List.of(first, second)))
                .thenReturn(List.of(item(first, 4.9, 20), item(second, 4.8, 40)));

        var result = mediaRankingService.topRated(MediaType.MOVIE, 1, 2);

        assertThat(result.items()).extracting(MediaSearchItemResponse::title)
                .containsExactly("Primeira", "Segunda");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void weightsRecentRatingsAboveLikesAndLibraryActivity() {
        Media ratedMedia = media("Nota recente");
        Media likedMedia = media("Curtidas recentes");
        Media libraryMedia = media("Biblioteca recente");
        Set<String> defaultTypes = Set.of("MOVIE", "SERIES", "ALBUM", "BOOK");
        RatingRepository.MediaActivityProjection ratingActivity = ratingActivity(ratedMedia.getId(), 2);
        MediaLikeRepository.MediaActivityProjection likeActivity = likeActivity(likedMedia.getId(), 2);
        UserMediaRepository.MediaActivityProjection libraryActivity = libraryActivity(libraryMedia.getId(), 3);

        when(ratingRepository.findRecentActivity(eq(defaultTypes), any(), any(), any()))
                .thenReturn(List.of(ratingActivity));
        when(mediaLikeRepository.findRecentActivity(eq(defaultTypes), any(), any()))
                .thenReturn(List.of(likeActivity));
        when(userMediaRepository.findRecentPublicActivity(eq(defaultTypes), any(), any()))
                .thenReturn(List.of(libraryActivity));
        when(mediaRepository.findAllById(any()))
                .thenReturn(List.of(ratedMedia, likedMedia, libraryMedia));
        when(mediaSearchItemAssembler.fromImported(any()))
                .thenReturn(List.of(
                        item(libraryMedia, 5.0, 1),
                        item(likedMedia, 4.5, 4),
                        item(ratedMedia, 4.0, 8)
                ));

        var result = mediaRankingService.trending(null, 7, 3);

        assertThat(result.items()).extracting(MediaSearchItemResponse::title)
                .containsExactly("Nota recente", "Curtidas recentes", "Biblioteca recente");
        assertThat(result.periodDays()).isEqualTo(7);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(ratingRepository).findRecentActivity(
                eq(defaultTypes), any(), since.capture(), any());
        assertThat(since.getValue()).isBetween(
                Instant.now().minusSeconds(7 * 24 * 60 * 60L + 5),
                Instant.now().minusSeconds(7 * 24 * 60 * 60L - 5)
        );
    }

    @Test
    void returnsFutureMoviesOrderedByPublicPlannedCount() {
        Media first = media("Mais aguardado");
        first.setReleaseDate(LocalDate.now().plusMonths(2));
        Media second = media("Segundo mais aguardado");
        second.setReleaseDate(LocalDate.now().plusMonths(1));
        UserMediaRepository.AnticipatedMediaProjection firstProjection =
                anticipated(first, 12);
        UserMediaRepository.AnticipatedMediaProjection secondProjection =
                anticipated(second, 7);
        when(userMediaRepository.findMostAnticipatedMovies(
                eq(UserMediaStatus.PLANNED), any(), eq(PageRequest.of(0, 6))))
                .thenReturn(List.of(firstProjection, secondProjection));
        when(mediaSearchItemAssembler.fromImported(List.of(first, second)))
                .thenReturn(List.of(item(first, 0, 0), item(second, 0, 0)));

        var result = mediaRankingService.anticipated(6);

        assertThat(result.items())
                .extracting(item -> item.title(), item -> item.plannedCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Mais aguardado", 12L),
                        org.assertj.core.groups.Tuple.tuple("Segundo mais aguardado", 7L)
                );
    }

    private Media media(String title) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        return media;
    }

    private MediaSearchItemResponse item(Media media, double average, long count) {
        return new MediaSearchItemResponse(
                media.getId(), media.getId().toString(), ExternalSource.MANUAL, media.getType(),
                media.getTitle(), null, null, null, null, true, average, count
        );
    }

    private RatingRepository.RatedMediaProjection rated(Media media) {
        var projection = mock(RatingRepository.RatedMediaProjection.class);
        when(projection.getMedia()).thenReturn(media);
        return projection;
    }

    private RatingRepository.MediaActivityProjection ratingActivity(UUID mediaId, long count) {
        var projection = mock(RatingRepository.MediaActivityProjection.class);
        when(projection.getMediaId()).thenReturn(mediaId);
        when(projection.getActivityCount()).thenReturn(count);
        return projection;
    }

    private MediaLikeRepository.MediaActivityProjection likeActivity(UUID mediaId, long count) {
        var projection = mock(MediaLikeRepository.MediaActivityProjection.class);
        when(projection.getMediaId()).thenReturn(mediaId);
        when(projection.getActivityCount()).thenReturn(count);
        return projection;
    }

    private UserMediaRepository.MediaActivityProjection libraryActivity(UUID mediaId, long count) {
        var projection = mock(UserMediaRepository.MediaActivityProjection.class);
        when(projection.getMediaId()).thenReturn(mediaId);
        when(projection.getActivityCount()).thenReturn(count);
        return projection;
    }

    private UserMediaRepository.AnticipatedMediaProjection anticipated(Media media, long count) {
        var projection = mock(UserMediaRepository.AnticipatedMediaProjection.class);
        when(projection.getMedia()).thenReturn(media);
        when(projection.getPlannedCount()).thenReturn(count);
        return projection;
    }
}
