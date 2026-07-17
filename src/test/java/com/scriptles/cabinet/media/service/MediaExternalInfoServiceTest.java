package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalInfoSnapshot;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaAvailabilityOffer;
import com.scriptles.cabinet.media.entity.MediaExternalRating;
import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import com.scriptles.cabinet.media.enums.ExternalInfoSectionState;
import com.scriptles.cabinet.media.enums.ExternalInfoSnapshotStatus;
import com.scriptles.cabinet.media.enums.ExternalOfferType;
import com.scriptles.cabinet.media.enums.ExternalRatingMetric;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalInfoSnapshotRepository;
import com.scriptles.cabinet.media.repository.MediaAvailabilityOfferRepository;
import com.scriptles.cabinet.media.repository.MediaExternalRatingRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaExternalInfoServiceTest {
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ExternalInfoSnapshotRepository snapshotRepository;
    @Mock
    private MediaAvailabilityOfferRepository availabilityOfferRepository;
    @Mock
    private MediaExternalRatingRepository externalRatingRepository;
    @Mock
    private ExternalInfoRefreshScheduler refreshScheduler;

    @InjectMocks
    private MediaExternalInfoService service;

    @Test
    void schedulesMissingSectionsAndReturnsPending() {
        Media movie = media(MediaType.MOVIE);
        when(mediaRepository.findById(movie.getId())).thenReturn(Optional.of(movie));
        when(snapshotRepository.findByMediaIdAndKindAndRegion(
                movie.getId(), ExternalInfoKind.AVAILABILITY, "BR"
        )).thenReturn(Optional.empty());
        when(snapshotRepository.findByMediaIdAndKindAndRegion(
                movie.getId(), ExternalInfoKind.RATINGS, MediaExternalInfoService.GLOBAL_REGION
        )).thenReturn(Optional.empty());
        when(availabilityOfferRepository
                .findAllByMediaIdAndCountryCodeOrderByDisplayPriorityAscProviderNameAsc(movie.getId(), "BR"))
                .thenReturn(List.of());
        when(externalRatingRepository.findAllByMediaIdOrderByMetricAsc(movie.getId())).thenReturn(List.of());

        var response = service.find(movie.getId(), "br");

        assertThat(response.countryCode()).isEqualTo("BR");
        assertThat(response.pendingWithoutData()).isTrue();
        assertThat(response.availability().state()).isEqualTo(ExternalInfoSectionState.PENDING);
        assertThat(response.ratings().state()).isEqualTo(ExternalInfoSectionState.PENDING);
        verify(refreshScheduler).scheduleAvailability(movie.getId(), "BR");
        verify(refreshScheduler).scheduleRatings(movie.getId());
    }

    @Test
    void servesPersistedDataAsStaleWhileSchedulingARefresh() {
        Media movie = media(MediaType.MOVIE);
        ExternalInfoSnapshot availabilitySnapshot = snapshot(
                movie, ExternalInfoKind.AVAILABILITY, "BR", ExternalInfoSnapshotStatus.READY);
        ExternalInfoSnapshot ratingSnapshot = snapshot(
                movie, ExternalInfoKind.RATINGS, MediaExternalInfoService.GLOBAL_REGION,
                ExternalInfoSnapshotStatus.READY);
        MediaAvailabilityOffer offer = offer(movie);
        MediaExternalRating rating = rating(movie);

        when(mediaRepository.findById(movie.getId())).thenReturn(Optional.of(movie));
        when(snapshotRepository.findByMediaIdAndKindAndRegion(
                movie.getId(), ExternalInfoKind.AVAILABILITY, "BR"
        )).thenReturn(Optional.of(availabilitySnapshot));
        when(snapshotRepository.findByMediaIdAndKindAndRegion(
                movie.getId(), ExternalInfoKind.RATINGS, MediaExternalInfoService.GLOBAL_REGION
        )).thenReturn(Optional.of(ratingSnapshot));
        when(availabilityOfferRepository
                .findAllByMediaIdAndCountryCodeOrderByDisplayPriorityAscProviderNameAsc(movie.getId(), "BR"))
                .thenReturn(List.of(offer));
        when(externalRatingRepository.findAllByMediaIdOrderByMetricAsc(movie.getId()))
                .thenReturn(List.of(rating));

        var response = service.find(movie.getId(), "BR");

        assertThat(response.pendingWithoutData()).isFalse();
        assertThat(response.availability().state()).isEqualTo(ExternalInfoSectionState.STALE);
        assertThat(response.availability().attributions()).containsExactly("JustWatch");
        assertThat(response.availability().offers()).singleElement().satisfies(item -> {
            assertThat(item.providerName()).isEqualTo("Netflix");
            assertThat(item.type()).isEqualTo(ExternalOfferType.SUBSCRIPTION);
        });
        assertThat(response.ratings().state()).isEqualTo(ExternalInfoSectionState.STALE);
        assertThat(response.ratings().items()).singleElement().satisfies(item -> {
            assertThat(item.source()).isEqualTo(ExternalSource.ROTTEN_TOMATOES);
            assertThat(item.metric()).isEqualTo(ExternalRatingMetric.TOMATOMETER);
            assertThat(item.displayValue()).isEqualTo("91%");
        });
        verify(refreshScheduler).scheduleAvailability(movie.getId(), "BR");
        verify(refreshScheduler).scheduleRatings(movie.getId());
    }

    @Test
    void reportsUnsupportedSectionsWithoutSchedulingProviders() {
        Media book = media(MediaType.BOOK);
        when(mediaRepository.findById(book.getId())).thenReturn(Optional.of(book));

        var response = service.find(book.getId(), "BR");

        assertThat(response.pendingWithoutData()).isFalse();
        assertThat(response.availability().state()).isEqualTo(ExternalInfoSectionState.NOT_SUPPORTED);
        assertThat(response.ratings().state()).isEqualTo(ExternalInfoSectionState.NOT_SUPPORTED);
        verify(refreshScheduler, never()).scheduleAvailability(book.getId(), "BR");
        verify(refreshScheduler, never()).scheduleRatings(book.getId());
    }

    private Media media(MediaType type) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(type);
        media.setTitle("Example");
        return media;
    }

    private ExternalInfoSnapshot snapshot(
            Media media,
            ExternalInfoKind kind,
            String region,
            ExternalInfoSnapshotStatus status
    ) {
        ExternalInfoSnapshot snapshot = new ExternalInfoSnapshot();
        snapshot.setMedia(media);
        snapshot.setKind(kind);
        snapshot.setRegion(region);
        snapshot.setStatus(status);
        snapshot.setFetchedAt(Instant.now().minusSeconds(7200));
        snapshot.setExpiresAt(Instant.now().minusSeconds(3600));
        return snapshot;
    }

    private MediaAvailabilityOffer offer(Media media) {
        MediaAvailabilityOffer offer = new MediaAvailabilityOffer();
        offer.setMedia(media);
        offer.setCountryCode("BR");
        offer.setDataSource(ExternalSource.JUSTWATCH);
        offer.setProviderId("8");
        offer.setProviderName("Netflix");
        offer.setOfferType(ExternalOfferType.SUBSCRIPTION);
        offer.setExternalUrl("https://www.themoviedb.org/movie/603/watch");
        offer.setSourceUrl("https://www.themoviedb.org/movie/603/watch");
        return offer;
    }

    private MediaExternalRating rating(Media media) {
        MediaExternalRating rating = new MediaExternalRating();
        rating.setMedia(media);
        rating.setProvider(ExternalSource.OMDB);
        rating.setSource(ExternalSource.ROTTEN_TOMATOES);
        rating.setMetric(ExternalRatingMetric.TOMATOMETER);
        rating.setValue(91.0);
        rating.setScale(100);
        rating.setDisplayValue("91%");
        rating.setExternalId("tt0133093");
        return rating;
    }
}
