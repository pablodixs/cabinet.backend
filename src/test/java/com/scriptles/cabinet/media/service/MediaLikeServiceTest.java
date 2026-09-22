package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserFeedService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaLikeServiceTest {
    @Mock
    private MediaLikeRepository mediaLikeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private UserFeedService userFeedService;

    @InjectMocks
    private MediaLikeService mediaLikeService;

    @Test
    void createsLikeWithoutCreatingLibraryEntry() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Media media = new Media();
        media.setId(mediaId);

        when(mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(mediaLikeRepository.insertIfAbsent(any(UUID.class), eq(userId), eq(mediaId)))
                .thenReturn(1);

        var response = mediaLikeService.like(userId, mediaId);

        verify(mediaLikeRepository).insertIfAbsent(any(UUID.class), eq(userId), eq(mediaId));
        assertThat(response.liked()).isTrue();
    }

    @Test
    void repeatedLikeIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId)).thenReturn(true);

        assertThat(mediaLikeService.like(userId, mediaId).liked()).isTrue();

        verify(mediaLikeRepository, never()).insertIfAbsent(any(), any(), any());
        verify(userRepository, never()).findById(userId);
        verify(mediaRepository, never()).findById(mediaId);
    }

    @Test
    void removesLikeIdempotently() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        mediaLikeService.unlike(userId, mediaId);

        verify(mediaLikeRepository).deleteByUserIdAndMediaId(userId, mediaId);
    }
}
