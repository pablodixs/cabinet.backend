package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.LibraryRatingFilter;
import com.scriptles.cabinet.user.enums.LibrarySort;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserMediaLibraryFilterRepositoryTest {
    @Autowired UserRepository userRepository;
    @Autowired UserMediaRepository userMediaRepository;
    @Autowired MediaRepository mediaRepository;
    @Autowired RatingRepository ratingRepository;

    @Test
    void combinesSearchGenreRatingAndPrivacyFilters() {
        User user = userRepository.save(User.create(
                "library-filter@example.com",
                "library-filter",
                "Library Filter",
                "hash"
        ));
        Media alpha = media(MediaType.MOVIE, "Alpha", "Drama");
        Media beta = media(MediaType.BOOK, "Beta", "Drama");
        Media gamma = media(MediaType.ALBUM, "Gamma", "Comedy");
        Media privateMedia = media(MediaType.SERIES, "Private Alpha", "Drama");

        libraryEntry(user, alpha, false);
        libraryEntry(user, beta, false);
        libraryEntry(user, gamma, false);
        libraryEntry(user, privateMedia, true);
        rating(user, alpha, "4.5", Visibility.PUBLIC);
        rating(user, gamma, "3.0", Visibility.PUBLIC);
        rating(user, privateMedia, "5.0", Visibility.PUBLIC);
        userMediaRepository.flush();

        var highlyRatedDrama = userMediaRepository.findProfileLibrary(
                user.getId(),
                false,
                null,
                null,
                "%alpha%",
                "Drama",
                LibraryRatingFilter.FOUR_PLUS.name(),
                List.of(Visibility.PUBLIC),
                LibrarySort.RATING.name(),
                PageRequest.of(0, 20)
        );
        var unratedDrama = userMediaRepository.findProfileLibrary(
                user.getId(),
                false,
                null,
                null,
                null,
                "Drama",
                LibraryRatingFilter.UNRATED.name(),
                List.of(Visibility.PUBLIC),
                LibrarySort.TITLE.name(),
                PageRequest.of(0, 20)
        );
        var oldestFirst = userMediaRepository.findProfileLibrary(
                user.getId(),
                false,
                null,
                null,
                null,
                null,
                LibraryRatingFilter.ALL.name(),
                List.of(Visibility.PUBLIC),
                LibrarySort.RELEASE_YEAR_ASC.name(),
                PageRequest.of(0, 20)
        );

        assertThat(highlyRatedDrama.getContent())
                .extracting(entry -> entry.getMedia().getTitle())
                .containsExactly("Alpha");
        assertThat(unratedDrama.getContent())
                .extracting(entry -> entry.getMedia().getTitle())
                .containsExactly("Beta");
        assertThat(userMediaRepository.findProfileLibraryGenres(user.getId(), false))
                .containsExactly("Comedy", "Drama");
        assertThat(oldestFirst.getContent())
                .extracting(entry -> entry.getMedia().getTitle())
                .containsExactly("Alpha", "Gamma", "Beta");
    }

    private Media media(MediaType type, String title, String genre) {
        Media media = new Media();
        media.setType(type);
        media.setTitle(title);
        media.setGenres(new LinkedHashSet<>(List.of(genre)));
        media.setReleaseDate(switch (title) {
            case "Alpha" -> LocalDate.of(1980, 1, 1);
            case "Gamma" -> LocalDate.of(1990, 1, 1);
            case "Beta" -> LocalDate.of(2000, 1, 1);
            default -> LocalDate.of(1970, 1, 1);
        });
        return mediaRepository.save(media);
    }

    private void libraryEntry(User user, Media media, boolean privateEntry) {
        UserMedia entry = new UserMedia();
        entry.setUser(user);
        entry.setMedia(media);
        entry.setStatus(UserMediaStatus.COMPLETED);
        entry.setFavorite(false);
        entry.setPrivateEntry(privateEntry);
        userMediaRepository.save(entry);
    }

    private void rating(User user, Media media, String value, Visibility visibility) {
        Rating rating = new Rating();
        rating.setUser(user);
        rating.setMedia(media);
        rating.setValue(new BigDecimal(value));
        rating.setVisibility(visibility);
        ratingRepository.save(rating);
    }
}
