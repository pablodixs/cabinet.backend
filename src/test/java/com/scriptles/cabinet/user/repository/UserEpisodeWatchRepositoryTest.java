package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserEpisodeWatchRepositoryTest {
    @Autowired UserEpisodeWatchRepository watchRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserMediaRepository userMediaRepository;
    @Autowired MediaRepository mediaRepository;
    @Autowired SeriesSeasonRepository seasonRepository;
    @Autowired SeriesEpisodeRepository episodeRepository;

    @Test
    void loadsAgendaEpisodesWithTheirMediaAndSeries() {
        User user = User.create("agenda@example.com", "agenda", "Agenda", "hash");
        userRepository.save(user);

        Media series = media(MediaType.SERIES, "Ruptura");
        mediaRepository.save(series);
        UserMedia entry = new UserMedia();
        entry.setUser(user);
        entry.setMedia(series);
        entry.setStatus(UserMediaStatus.IN_PROGRESS);
        entry.setFavorite(false);
        entry.setPrivateEntry(false);
        userMediaRepository.save(entry);

        SeriesSeason season = new SeriesSeason();
        season.setSeries(series);
        season.setSeasonNumber(2);
        seasonRepository.save(season);

        SeriesEpisode overdue = episode(season, 1, LocalDate.now().minusDays(1));
        SeriesEpisode upcoming = episode(season, 2, LocalDate.now().plusDays(2));
        episodeRepository.save(overdue);
        episodeRepository.save(upcoming);

        assertThat(watchRepository.findOverdue(
                user.getId(), LocalDate.now(), PageRequest.of(0, 50)))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getEpisodeMedia().getTitle()).isEqualTo("Episódio 1");
                    assertThat(item.getSeason().getSeries().getTitle()).isEqualTo("Ruptura");
                });
        assertThat(watchRepository.findUpcoming(
                user.getId(), LocalDate.now(), LocalDate.now().plusDays(30)))
                .singleElement()
                .extracting(SeriesEpisode::getEpisodeNumber)
                .isEqualTo(2);
    }

    private SeriesEpisode episode(SeriesSeason season, int number, LocalDate airDate) {
        Media episodeMedia = media(MediaType.EPISODE, "Episódio " + number);
        episodeMedia.setReleaseDate(airDate);
        mediaRepository.save(episodeMedia);
        SeriesEpisode episode = new SeriesEpisode();
        episode.setSeason(season);
        episode.setEpisodeMedia(episodeMedia);
        episode.setEpisodeNumber(number);
        episode.setTitle(episodeMedia.getTitle());
        episode.setAirDate(airDate);
        return episode;
    }

    private Media media(MediaType type, String title) {
        Media media = new Media();
        media.setType(type);
        media.setTitle(title);
        return media;
    }
}
