package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserProfileFavorite;
import com.scriptles.cabinet.user.repository.UserProfileFavoriteRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProfileFavoriteServiceTest {
    @Mock
    private UserProfileFavoriteRepository favoriteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private UserMediaRepository userMediaRepository;
    @InjectMocks
    private ProfileFavoriteService service;

    @Test
    void replacesFavoritesInTheRequestedOrder() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Media first = media();
        Media second = media();
        List<UUID> mediaIds = List.of(first.getId(), second.getId());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findAllById(any())).thenReturn(List.of(first, second));
        when(userMediaRepository.existsByUserIdAndMediaId(userId, first.getId()))
                .thenReturn(true);
        when(userMediaRepository.existsByUserIdAndMediaId(userId, second.getId()))
                .thenReturn(true);

        service.replace(userId, mediaIds);

        verify(favoriteRepository).deleteAllByUserId(userId);
        ArgumentCaptor<List<UserProfileFavorite>> captor = ArgumentCaptor.forClass(List.class);
        verify(favoriteRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(UserProfileFavorite::getPosition)
                .containsExactly(0, 1);
        assertThat(captor.getValue())
                .extracting(favorite -> favorite.getMedia().getId())
                .containsExactlyElementsOf(mediaIds);
    }

    @Test
    void rejectsMoreThanFourFavorites() {
        List<UUID> mediaIds = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.replace(UUID.randomUUID(), mediaIds))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("quatro");
    }

    @Test
    void rejectsFavoriteOutsideTheUsersLibrary() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(userMediaRepository.existsByUserIdAndMediaId(userId, mediaId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.replace(userId, List.of(mediaId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("biblioteca");
    }

    private Media media() {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        return media;
    }
}
