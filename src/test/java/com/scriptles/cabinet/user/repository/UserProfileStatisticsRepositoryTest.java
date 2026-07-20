package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MovieDetails;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserEpisodeWatch;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserProfileStatisticsRepositoryTest {
    @Autowired UserRepository userRepository;
    @Autowired UserMediaRepository userMediaRepository;
    @Autowired UserEpisodeWatchRepository watchRepository;
    @Autowired MediaRepository mediaRepository;
    @Autowired MovieDetailsRepository movieDetailsRepository;
    @Autowired BookDetailsRepository bookDetailsRepository;
    @Autowired SeriesSeasonRepository seasonRepository;
    @Autowired SeriesEpisodeRepository episodeRepository;

    @Test
    void aggregatesConsumptionAndHonorsSeriesEntryPrivacy() {
        User user = userRepository.save(User.create(
                "profile-stats@example.com", "profile-stats", "Profile Stats", "hash"));

        Media movie = media(MediaType.MOVIE, "Movie");
        MovieDetails movieDetails = new MovieDetails();
        movieDetails.setMedia(movie);
        movieDetails.setRuntimeMinutes(120);
        movieDetailsRepository.save(movieDetails);
        libraryEntry(user, movie, UserMediaStatus.COMPLETED, false);

        Media album = media(MediaType.ALBUM, "Album");
        libraryEntry(user, album, UserMediaStatus.COMPLETED, false);

        Media book = media(MediaType.BOOK, "Private book");
        BookDetails bookDetails = new BookDetails();
        bookDetails.setMedia(book);
        bookDetails.setPageCount(300);
        bookDetailsRepository.save(bookDetails);
        libraryEntry(user, book, UserMediaStatus.COMPLETED, true);

        Media publicSeries = media(MediaType.SERIES, "Public series");
        libraryEntry(user, publicSeries, UserMediaStatus.IN_PROGRESS, false);
        SeriesSeason publicSeason = season(publicSeries, 1);
        watch(user, episode(publicSeason, 1, 45));
        watch(user, episode(publicSeason, 2, 55));

        Media privateSeries = media(MediaType.SERIES, "Private series");
        libraryEntry(user, privateSeries, UserMediaStatus.IN_PROGRESS, true);
        SeriesSeason privateSeason = season(privateSeries, 1);
        watch(user, episode(privateSeason, 1, 40));

        userMediaRepository.flush();

        UserMediaRepository.ProfileStatisticsProjection publicStats =
                userMediaRepository.findProfileStatistics(user.getId(), false);
        assertThat(publicStats.getLibraryCount()).isEqualTo(3);
        assertThat(publicStats.getCompletedCount()).isEqualTo(2);
        assertThat(publicStats.getInProgressCount()).isEqualTo(1);
        assertThat(publicStats.getWatchedMinutes()).isEqualTo(220);
        assertThat(publicStats.getPagesRead()).isZero();
        assertThat(publicStats.getEpisodesWatched()).isEqualTo(2);
        assertThat(publicStats.getAlbumsConsumed()).isEqualTo(1);
        assertThat(publicStats.getMoviesConsumed()).isEqualTo(1);
        assertThat(publicStats.getSeriesConsumed()).isEqualTo(1);
        assertThat(publicStats.getBooksConsumed()).isZero();

        UserMediaRepository.ProfileStatisticsProjection ownStats =
                userMediaRepository.findProfileStatistics(user.getId(), true);
        assertThat(ownStats.getLibraryCount()).isEqualTo(5);
        assertThat(ownStats.getCompletedCount()).isEqualTo(3);
        assertThat(ownStats.getInProgressCount()).isEqualTo(2);
        assertThat(ownStats.getWatchedMinutes()).isEqualTo(260);
        assertThat(ownStats.getPagesRead()).isEqualTo(300);
        assertThat(ownStats.getEpisodesWatched()).isEqualTo(3);
        assertThat(ownStats.getAlbumsConsumed()).isEqualTo(1);
        assertThat(ownStats.getMoviesConsumed()).isEqualTo(1);
        assertThat(ownStats.getSeriesConsumed()).isEqualTo(2);
        assertThat(ownStats.getBooksConsumed()).isEqualTo(1);
    }

    private Media media(MediaType type, String title) {
        Media media = new Media();
        media.setType(type);
        media.setTitle(title);
        return mediaRepository.save(media);
    }

    private void libraryEntry(
            User user,
            Media media,
            UserMediaStatus status,
            boolean privateEntry
    ) {
        UserMedia entry = new UserMedia();
        entry.setUser(user);
        entry.setMedia(media);
        entry.setStatus(status);
        entry.setFavorite(false);
        entry.setPrivateEntry(privateEntry);
        userMediaRepository.save(entry);
    }

    private SeriesSeason season(Media series, int number) {
        SeriesSeason season = new SeriesSeason();
        season.setSeries(series);
        season.setSeasonNumber(number);
        return seasonRepository.save(season);
    }

    private SeriesEpisode episode(SeriesSeason season, int number, int runtimeMinutes) {
        Media episodeMedia = media(MediaType.EPISODE, "Episode " + number);
        SeriesEpisode episode = new SeriesEpisode();
        episode.setSeason(season);
        episode.setEpisodeMedia(episodeMedia);
        episode.setEpisodeNumber(number);
        episode.setTitle(episodeMedia.getTitle());
        episode.setRuntimeMinutes(runtimeMinutes);
        return episodeRepository.save(episode);
    }

    private void watch(User user, SeriesEpisode episode) {
        UserEpisodeWatch watch = new UserEpisodeWatch();
        watch.setUser(user);
        watch.setEpisode(episode);
        watch.setWatchedAt(Instant.now());
        watchRepository.save(watch);
    }
}
