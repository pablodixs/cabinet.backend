package com.scriptles.cabinet.comments.service;

import com.scriptles.cabinet.comments.dto.CommentRequest;
import com.scriptles.cabinet.comments.dto.CommentResponse;
import com.scriptles.cabinet.comments.dto.UpdateCommentRequest;
import com.scriptles.cabinet.comments.entity.Comment;
import com.scriptles.cabinet.comments.repository.CommentRepository;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.SocialAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final MediaListRepository mediaListRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SocialAccessPolicy socialAccessPolicy;

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> findForList(UUID viewerId, UUID listId, int page, int size) {
        requireAccessibleList(viewerId, listId);
        Page<Comment> comments = viewerId == null
                ? commentRepository.findByMediaListIdAndParentIsNull(listId, pageRequest(page, size))
                : commentRepository.findVisibleByMediaListId(listId, viewerId, pageRequest(page, size));
        return responses(comments, viewerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> findForReview(UUID viewerId, UUID reviewId, int page, int size) {
        requireAccessibleReview(viewerId, reviewId);
        Page<Comment> comments = viewerId == null
                ? commentRepository.findByReviewIdAndParentIsNull(reviewId, pageRequest(page, size))
                : commentRepository.findVisibleByReviewId(reviewId, viewerId, pageRequest(page, size));
        return responses(comments, viewerId);
    }

    @Transactional
    public CommentResponse createForList(UUID authorId, UUID listId, CommentRequest request) {
        MediaList list = requireAccessibleList(authorId, listId);
        Comment comment = newComment(authorId, request);
        comment.setMediaList(list);
        applyParent(comment, request.parentId(), listId, null);
        return saveAndNotify(comment, authorId);
    }

    @Transactional
    public CommentResponse createForReview(UUID authorId, UUID reviewId, CommentRequest request) {
        Review review = requireAccessibleReview(authorId, reviewId);
        Comment comment = newComment(authorId, request);
        comment.setReview(review);
        applyParent(comment, request.parentId(), null, reviewId);
        return saveAndNotify(comment, authorId);
    }

    @Transactional
    public CommentResponse update(UUID authorId, UUID commentId, UpdateCommentRequest request) {
        Comment comment = requireOwnedActive(authorId, commentId);
        comment.setContent(request.content().trim());
        return CommentResponse.from(commentRepository.saveAndFlush(comment), authorId, List.of());
    }

    @Transactional
    public void delete(UUID authorId, UUID commentId) {
        Comment comment = requireOwnedActive(authorId, commentId);
        notificationService.commentDeleted(commentId);
        if (comment.getParent() == null && commentRepository.existsByParentId(commentId)) {
            comment.setContent(null);
            comment.setDeletedAt(Instant.now());
            commentRepository.save(comment);
        } else {
            commentRepository.delete(comment);
        }
    }

    private CommentResponse saveAndNotify(Comment comment, UUID authorId) {
        Comment saved = commentRepository.saveAndFlush(comment);
        notificationService.commentCreated(saved);
        return CommentResponse.from(saved, authorId, List.of());
    }

    private Comment newComment(UUID authorId, CommentRequest request) {
        User author = userRepository.findById(authorId).orElseThrow(() -> notFound("USER_NOT_FOUND", "Usuário não encontrado"));
        Comment comment = new Comment();
        comment.setAuthor(author);
        comment.setContent(request.content().trim());
        return comment;
    }

    private void applyParent(Comment comment, UUID parentId, UUID listId, UUID reviewId) {
        if (parentId == null) return;
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> notFound("COMMENT_NOT_FOUND", "Comentário não encontrado"));
        boolean sameList = listId != null && parent.getMediaList() != null
                && listId.equals(parent.getMediaList().getId());
        boolean sameReview = reviewId != null && parent.getReview() != null
                && reviewId.equals(parent.getReview().getId());
        if ((!sameList && !sameReview) || parent.getParent() != null || parent.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMENT_PARENT",
                    "A resposta deve apontar para um comentário raiz ativo do mesmo conteúdo");
        }
        if (socialAccessPolicy != null
                && socialAccessPolicy.isBlocked(comment.getAuthor().getId(), parent.getAuthor().getId())) {
            throw notFound("COMMENT_NOT_FOUND", "Comentário não encontrado");
        }
        comment.setParent(parent);
    }

    private PageResponse<CommentResponse> responses(Page<Comment> roots, UUID viewerId) {
        List<UUID> rootIds = roots.getContent().stream().map(Comment::getId).toList();
        List<Comment> replyRows = rootIds.isEmpty() ? List.of()
                : viewerId == null
                    ? commentRepository.findByParentIdInOrderByCreatedAtAscIdAsc(rootIds)
                    : commentRepository.findVisibleReplies(rootIds, viewerId);
        Map<UUID, List<Comment>> replies = replyRows.stream()
                .collect(Collectors.groupingBy(reply -> reply.getParent().getId(), LinkedHashMap::new, Collectors.toList()));
        List<CommentResponse> items = roots.getContent().stream()
                .map(root -> CommentResponse.from(
                        root,
                        viewerId,
                        replies.getOrDefault(root.getId(), List.of()).stream()
                                .map(reply -> CommentResponse.from(reply, viewerId, List.of()))
                                .toList()))
                .toList();
        return new PageResponse<>(items, roots.getNumber(), roots.getSize(), roots.getTotalElements(), roots.getTotalPages());
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private MediaList requireAccessibleList(UUID viewerId, UUID listId) {
        return mediaListRepository.findWithOwnerById(listId)
                .filter(list -> socialAccessPolicy == null
                        ? list.getVisibility() == Visibility.PUBLIC
                        : socialAccessPolicy.canViewContent(
                                list.getOwner().getId(), viewerId, list.getVisibility()))
                .orElseThrow(() -> notFound("LIST_NOT_FOUND", "Lista não encontrada"));
    }

    private Review requireAccessibleReview(UUID viewerId, UUID reviewId) {
        return (socialAccessPolicy == null
                ? reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC)
                : reviewRepository.findById(reviewId)
                    .filter(review -> review.getContent() != null && !review.getContent().isBlank())
                    .filter(review -> socialAccessPolicy.canViewContent(
                            review.getUser().getId(), viewerId, review.getVisibility())))
                .orElseThrow(() -> notFound("REVIEW_NOT_FOUND", "Review não encontrada"));
    }

    private Comment requireOwnedActive(UUID authorId, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> notFound("COMMENT_NOT_FOUND", "Comentário não encontrado"));
        if (!comment.getAuthor().getId().equals(authorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMMENT_FORBIDDEN", "Você não pode alterar este comentário");
        }
        if (comment.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMENT_DELETED", "Este comentário foi removido");
        }
        return comment;
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
