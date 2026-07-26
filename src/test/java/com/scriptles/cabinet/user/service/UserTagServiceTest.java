package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaTag;
import com.scriptles.cabinet.user.entity.UserTag;
import com.scriptles.cabinet.user.repository.UserMediaTagRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.repository.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTagServiceTest {
    @Mock UserTagRepository tagRepository;
    @Mock UserMediaTagRepository mediaTagRepository;
    @Mock UserRepository userRepository;
    @Mock MediaRepository mediaRepository;
    @InjectMocks UserTagService service;

    @Test
    void createsATrimmedTagWithoutTheHashPrefix() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tagRepository.findByUserIdAndNormalizedName(
                userId, "para rever")).thenReturn(Optional.empty());
        when(tagRepository.countByUserId(userId)).thenReturn(3L);
        when(tagRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            return tag;
        });

        var response = service.create(userId, "  # Para Rever  ");

        assertThat(response.name()).isEqualTo("Para Rever");
        ArgumentCaptor<UserTag> tag = ArgumentCaptor.forClass(UserTag.class);
        verify(tagRepository).saveAndFlush(tag.capture());
        assertThat(tag.getValue().getNormalizedName()).isEqualTo("para rever");
    }

    @Test
    void rejectsTheSameTagIgnoringCase() {
        UUID userId = UUID.randomUUID();
        when(tagRepository.findByUserIdAndNormalizedName(userId, "cinema"))
                .thenReturn(Optional.of(new UserTag()));

        assertThatThrownBy(() -> service.create(userId, "CINEMA"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("já criou");
    }

    @Test
    void replacesTagsAssignedToAMediaAndCreatesMissingCatalogTags() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media media = new Media();
        media.setId(mediaId);
        UserTag existing = tag(user, "Cinema", "cinema");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(tagRepository.findAllByUserIdAndNormalizedNameIn(
                userId, Set.of("cinema", "com amigos")))
                .thenReturn(List.of(existing));
        when(tagRepository.countByUserId(userId)).thenReturn(1L);
        when(tagRepository.save(any())).thenAnswer(invocation -> {
            UserTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            return tag;
        });

        List<String> result = service.replaceMediaTags(
                userId, mediaId, List.of("Cinema", "#com amigos"));

        assertThat(result).containsExactly("Cinema", "com amigos");
        verify(mediaTagRepository).deleteAllByUserIdAndMediaId(userId, mediaId);
        ArgumentCaptor<List<UserMediaTag>> saved = ArgumentCaptor.forClass(List.class);
        verify(mediaTagRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private UserTag tag(User user, String name, String normalizedName) {
        UserTag tag = new UserTag();
        tag.setId(UUID.randomUUID());
        tag.setUser(user);
        tag.setName(name);
        tag.setNormalizedName(normalizedName);
        return tag;
    }
}
