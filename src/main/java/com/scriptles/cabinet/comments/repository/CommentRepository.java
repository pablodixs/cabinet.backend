package com.scriptles.cabinet.comments.repository;

import com.scriptles.cabinet.comments.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
