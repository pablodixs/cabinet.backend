package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserFeedActivity;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserFeedActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFeedServiceTest {
    @Mock UserFeedActivityRepository repository;
    @InjectMocks UserFeedService service;

    @Test
    void createsFeedActionWithItsVisibilityAndDetails() {
        User user = new User();
        user.setId(UUID.randomUUID());
        Media media = new Media();
        media.setId(UUID.randomUUID());
        Instant occurredAt = Instant.parse("2026-09-22T10:00:00Z");
        when(repository.findByUserIdAndMediaIdAndActionType(user.getId(), media.getId(), FeedActionType.REVIEWED))
                .thenReturn(Optional.empty());

        service.record(user, media, FeedActionType.REVIEWED, occurredAt, Visibility.FOLLOWERS,
                new BigDecimal("4.5"), "A good read", true);

        ArgumentCaptor<UserFeedActivity> captor = ArgumentCaptor.forClass(UserFeedActivity.class);
        verify(repository).save(captor.capture());
        UserFeedActivity saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getMedia()).isSameAs(media);
        assertThat(saved.getActionType()).isEqualTo(FeedActionType.REVIEWED);
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getVisibility()).isEqualTo(Visibility.FOLLOWERS);
        assertThat(saved.getRating()).isEqualByComparingTo("4.5");
        assertThat(saved.getReview()).isEqualTo("A good read");
        assertThat(saved.isContainsSpoilers()).isTrue();
    }

    @Test
    void updatesTheExistingActionInsteadOfCreatingAnotherFeedRow() {
        User user = new User();
        user.setId(UUID.randomUUID());
        Media media = new Media();
        media.setId(UUID.randomUUID());
        UserFeedActivity existing = new UserFeedActivity();
        existing.setId(UUID.randomUUID());
        when(repository.findByUserIdAndMediaIdAndActionType(user.getId(), media.getId(), FeedActionType.RATED))
                .thenReturn(Optional.of(existing));

        service.record(user, media, FeedActionType.RATED, Instant.now(), Visibility.PUBLIC,
                new BigDecimal("5.0"), null, false);

        ArgumentCaptor<UserFeedActivity> captor = ArgumentCaptor.forClass(UserFeedActivity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void removesAnActionWhenItsSourceActionIsDeleted() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        service.remove(userId, mediaId, FeedActionType.LIKED);

        verify(repository).deleteByUserIdAndMediaIdAndActionType(userId, mediaId, FeedActionType.LIKED);
    }
}
