package com.scriptles.cabinet.comments.service;

import com.scriptles.cabinet.comments.dto.CommentRequest;
import com.scriptles.cabinet.comments.entity.Comment;
import com.scriptles.cabinet.comments.repository.CommentRepository;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock CommentRepository commentRepository;
    @Mock MediaListRepository mediaListRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @InjectMocks CommentService commentService;

    @Test
    void createsOneLevelReplyAndNotifiesAfterPersistence() {
        UUID authorId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        User author = user(authorId);
        MediaList list = publicList(listId);
        Comment parent = new Comment();
        parent.setId(parentId);
        parent.setAuthor(user(UUID.randomUUID()));
        parent.setMediaList(list);
        when(mediaListRepository.findWithOwnerById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        commentService.createForList(authorId, listId, new CommentRequest("  resposta  ", parentId));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("resposta");
        assertThat(captor.getValue().getParent()).isSameAs(parent);
        verify(notificationService).commentCreated(captor.getValue());
    }

    @Test
    void rejectsReplyToAReply() {
        UUID authorId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Review review = new Review();
        review.setId(reviewId);
        Comment parent = new Comment();
        parent.setId(parentId);
        parent.setReview(review);
        parent.setParent(new Comment());
        when(reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC)).thenReturn(Optional.of(review));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(user(authorId)));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createForReview(
                authorId, reviewId, new CommentRequest("resposta", parentId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("comentário raiz");

        verify(commentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(notificationService);
    }

    private MediaList publicList(UUID id) {
        MediaList list = new MediaList();
        list.setId(id);
        list.setVisibility(Visibility.PUBLIC);
        list.setOwner(user(UUID.randomUUID()));
        return list;
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user");
        user.setDisplayName("User");
        return user;
    }
}
