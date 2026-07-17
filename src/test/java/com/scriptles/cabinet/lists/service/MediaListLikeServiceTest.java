package com.scriptles.cabinet.lists.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaListLikeServiceTest {
    @Mock
    private MediaListLikeRepository mediaListLikeRepository;
    @Mock
    private MediaListRepository mediaListRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MediaListLikeService mediaListLikeService;

    @Test
    void likesPublicListAndReturnsUpdatedCount() {
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        MediaList list = publicList(listId);

        when(mediaListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(mediaListLikeRepository.existsByUserIdAndListId(userId, listId))
                .thenReturn(false, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaListLikeRepository.insertIfAbsent(any(UUID.class), eq(userId), eq(listId)))
                .thenReturn(1);
        when(mediaListLikeRepository.countByListId(listId)).thenReturn(6L);

        var response = mediaListLikeService.like(userId, listId);

        verify(mediaListLikeRepository).insertIfAbsent(any(UUID.class), eq(userId), eq(listId));
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(6);
    }

    @Test
    void repeatedLikeIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        when(mediaListRepository.findById(listId))
                .thenReturn(Optional.of(publicList(listId)));
        when(mediaListLikeRepository.existsByUserIdAndListId(userId, listId))
                .thenReturn(true);
        when(mediaListLikeRepository.countByListId(listId)).thenReturn(3L);

        var response = mediaListLikeService.like(userId, listId);

        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(3);
        verify(mediaListLikeRepository, never()).insertIfAbsent(any(), any(), any());
        verify(userRepository, never()).findById(userId);
    }

    @Test
    void rejectsLikeForPrivateList() {
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        MediaList list = publicList(listId);
        list.setVisibility(Visibility.PRIVATE);
        when(mediaListRepository.findById(listId)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> mediaListLikeService.like(userId, listId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Lista não encontrada");

        verify(mediaListLikeRepository, never()).insertIfAbsent(any(), any(), any());
    }

    private MediaList publicList(UUID listId) {
        MediaList list = new MediaList();
        list.setId(listId);
        list.setVisibility(Visibility.PUBLIC);
        return list;
    }
}
