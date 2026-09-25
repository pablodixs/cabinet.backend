package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRankingSnapshotRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
    private UserMediaRepository userMediaRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MediaSearchItemAssembler mediaSearchItemAssembler;
    @Mock
    private MediaRankingSnapshotRepository rankingSnapshotRepository;

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
        when(mediaSearchItemAssembler.fromImported(List.of(first, second), "en-US"))
                .thenReturn(List.of(item(first, 4.9, 20), item(second, 4.8, 40)));

        var result = mediaRankingService.topRated(MediaType.MOVIE, 1, 2, "en-US");

        assertThat(result.items()).extracting(MediaSearchItemResponse::title)
                .containsExactly("Primeira", "Segunda");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void preservesTrendingSnapshotRepositoryOrder() {
        Media ratedMedia = media("Nota recente");
        Media likedMedia = media("Curtidas recentes");
        Media libraryMedia = media("Biblioteca recente");
        Set<String> defaultTypes = Set.of("MOVIE", "SERIES", "ALBUM", "BOOK");
        List<UUID> rankedIds = List.of(ratedMedia.getId(), likedMedia.getId(), libraryMedia.getId());
        when(rankingSnapshotRepository.findTrendingMediaIds(defaultTypes, 7, 3)).thenReturn(rankedIds);
        when(mediaRepository.findAllById(any()))
                .thenReturn(List.of(libraryMedia, ratedMedia, likedMedia));
        when(mediaSearchItemAssembler.fromImported(List.of(ratedMedia, likedMedia, libraryMedia), "en-US"))
                .thenReturn(List.of(
                        item(ratedMedia, 5.0, 1),
                        item(likedMedia, 4.5, 4),
                        item(libraryMedia, 4.0, 8)
                ));

        var result = mediaRankingService.trending(null, 7, 3, "en-US");

        assertThat(result.items()).extracting(MediaSearchItemResponse::title)
                .containsExactly("Nota recente", "Curtidas recentes", "Biblioteca recente");
        assertThat(result.periodDays()).isEqualTo(7);

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
        when(mediaSearchItemAssembler.fromImported(List.of(first, second), "en-US"))
                .thenReturn(List.of(item(first, 0, 0), item(second, 0, 0)));

        var result = mediaRankingService.anticipated(6, "en-US");

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

    private UserMediaRepository.AnticipatedMediaProjection anticipated(Media media, long count) {
        var projection = mock(UserMediaRepository.AnticipatedMediaProjection.class);
        when(projection.getMedia()).thenReturn(media);
        when(projection.getPlannedCount()).thenReturn(count);
        return projection;
    }
}
