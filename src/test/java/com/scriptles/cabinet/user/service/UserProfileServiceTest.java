package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMediaRepository userMediaRepository;

    @Mock
    private ExternalReferenceRepository externalReferenceRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void returnsOnlyPublicProfileDataToOtherUsers() {
        User profileUser = user(Visibility.PUBLIC);
        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaRepository.findProfileLibrary(
                eq(profileUser.getId()),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        when(userMediaRepository.countProfileLibrary(profileUser.getId(), null, false))
                .thenReturn(12L);
        when(userMediaRepository.countProfileLibrary(
                profileUser.getId(),
                UserMediaStatus.COMPLETED,
                false
        )).thenReturn(7L);
        when(userMediaRepository.countProfileLibrary(
                profileUser.getId(),
                UserMediaStatus.IN_PROGRESS,
                false
        )).thenReturn(2L);

        UserProfileResponse response = userProfileService.findByUsername(
                " maria ",
                UUID.randomUUID()
        );

        assertThat(response.username()).isEqualTo("maria");
        assertThat(response.email()).isNull();
        assertThat(response.ownProfile()).isFalse();
        assertThat(response.libraryCount()).isEqualTo(12);
        assertThat(response.completedCount()).isEqualTo(7);
        assertThat(response.inProgressCount()).isEqualTo(2);
        assertThat(response.recentItems()).isEmpty();
    }

    @Test
    void includesPrivateEntriesAndEmailForTheProfileOwner() {
        User profileUser = user(Visibility.PRIVATE);
        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaRepository.findProfileLibrary(
                eq(profileUser.getId()),
                eq(true),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        UserProfileResponse response = userProfileService.findByUsername(
                "maria",
                profileUser.getId()
        );

        assertThat(response.ownProfile()).isTrue();
        assertThat(response.email()).isEqualTo("maria@example.com");
        verify(userMediaRepository).countProfileLibrary(profileUser.getId(), null, true);
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
        UserMedia entry = new UserMedia();
        entry.setId(UUID.randomUUID());
        entry.setUser(profileUser);
        entry.setMedia(media);
        entry.setStatus(UserMediaStatus.COMPLETED);
        entry.setLastInteractionAt(occurredAt);
        entry.setPrivateEntry(false);

        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.GOOGLE_BOOKS);
        reference.setExternalId("book-123");
        reference.setPrimaryReference(true);

        when(userRepository.findByUsernameIgnoreCase("maria"))
                .thenReturn(Optional.of(profileUser));
        when(userMediaRepository.findProfileActivities(
                eq(profileUser.getId()),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(entry)));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                List.of(media.getId())
        )).thenReturn(List.of(reference));

        ProfileActivityResponse activity = userProfileService.findActivities(
                "maria",
                UUID.randomUUID(),
                0,
                20
        ).items().getFirst();

        assertThat(activity.type()).isEqualTo(ProfileActivityType.COMPLETED);
        assertThat(activity.occurredAt()).isEqualTo(occurredAt);
        assertThat(activity.title()).isEqualTo("Torto Arado");
        assertThat(activity.source()).isEqualTo(ExternalSource.GOOGLE_BOOKS);
        assertThat(activity.externalId()).isEqualTo("book-123");
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

        verify(userMediaRepository, never()).findProfileLibrary(
                any(UUID.class),
                anyBoolean(),
                any(Pageable.class)
        );
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
}
