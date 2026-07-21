package com.scriptles.cabinet.comments.repository;

import com.scriptles.cabinet.comments.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    boolean existsByParentId(UUID parentId);

    @EntityGraph(attributePaths = {"author"})
    Page<Comment> findByMediaListIdAndParentIsNull(UUID listId, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Page<Comment> findByReviewIdAndParentIsNull(UUID reviewId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "parent"})
    List<Comment> findByParentIdInOrderByCreatedAtAscIdAsc(Collection<UUID> parentIds);

    @EntityGraph(attributePaths = {"author"})
    @Query(value = """
            select comment from Comment comment
            where comment.mediaList.id = :listId and comment.parent is null
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = comment.author.id)
                     or (block.blocker.id = comment.author.id and block.blocked.id = :viewerId))
            """, countQuery = """
            select count(comment) from Comment comment
            where comment.mediaList.id = :listId and comment.parent is null
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = comment.author.id)
                     or (block.blocker.id = comment.author.id and block.blocked.id = :viewerId))
            """)
    Page<Comment> findVisibleByMediaListId(
            @Param("listId") UUID listId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    @Query(value = """
            select comment from Comment comment
            where comment.review.id = :reviewId and comment.parent is null
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = comment.author.id)
                     or (block.blocker.id = comment.author.id and block.blocked.id = :viewerId))
            """, countQuery = """
            select count(comment) from Comment comment
            where comment.review.id = :reviewId and comment.parent is null
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = comment.author.id)
                     or (block.blocker.id = comment.author.id and block.blocked.id = :viewerId))
            """)
    Page<Comment> findVisibleByReviewId(
            @Param("reviewId") UUID reviewId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"author", "parent"})
    @Query("""
            select comment from Comment comment
            where comment.parent.id in :parentIds
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = comment.author.id)
                     or (block.blocker.id = comment.author.id and block.blocked.id = :viewerId))
            order by comment.createdAt asc, comment.id asc
            """)
    List<Comment> findVisibleReplies(
            @Param("parentIds") Collection<UUID> parentIds,
            @Param("viewerId") UUID viewerId);
}
