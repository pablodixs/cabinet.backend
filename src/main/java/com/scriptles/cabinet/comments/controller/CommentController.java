package com.scriptles.cabinet.comments.controller;

import com.scriptles.cabinet.comments.dto.CommentRequest;
import com.scriptles.cabinet.comments.dto.CommentResponse;
import com.scriptles.cabinet.comments.dto.UpdateCommentRequest;
import com.scriptles.cabinet.comments.service.CommentService;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping("/v1/lists/{listId}/comments")
    public PageResponse<CommentResponse> findForList(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return commentService.findForList(user == null ? null : user.id(), listId, page, size);
    }

    @GetMapping("/v1/reviews/{reviewId}/comments")
    public PageResponse<CommentResponse> findForReview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID reviewId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return commentService.findForReview(user == null ? null : user.id(), reviewId, page, size);
    }

    @PostMapping("/v1/me/lists/{listId}/comments")
    public CommentResponse createForList(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID listId,
                                         @RequestBody @Valid CommentRequest request) {
        return commentService.createForList(user.id(), listId, request);
    }

    @PostMapping("/v1/me/reviews/{reviewId}/comments")
    public CommentResponse createForReview(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID reviewId,
                                           @RequestBody @Valid CommentRequest request) {
        return commentService.createForReview(user.id(), reviewId, request);
    }

    @PatchMapping("/v1/me/comments/{commentId}")
    public CommentResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable UUID commentId,
                                  @RequestBody @Valid UpdateCommentRequest request) {
        return commentService.update(user.id(), commentId, request);
    }

    @DeleteMapping("/v1/me/comments/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID commentId) {
        commentService.delete(user.id(), commentId);
        return ResponseEntity.noContent().build();
    }
}
