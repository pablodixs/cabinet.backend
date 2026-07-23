package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserBlock;
import com.scriptles.cabinet.user.entity.UserBlockId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {
    @Query("""
            select count(block) > 0 from UserBlock block
            where (block.blocker.id = :firstId and block.blocked.id = :secondId)
               or (block.blocker.id = :secondId and block.blocked.id = :firstId)
            """)
    boolean existsEitherDirection(
            @Param("firstId") UUID firstId,
            @Param("secondId") UUID secondId
    );

    @EntityGraph(attributePaths = "blocked")
    @Query("""
            select block from UserBlock block
            where block.blocker.id = :userId
            order by block.createdAt desc, block.blocked.id desc
            """)
    List<UserBlock> findFirstBlockedUsers(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "blocked")
    @Query("""
            select block from UserBlock block
            where block.blocker.id = :userId
              and (block.createdAt < :cursorTime
                   or (block.createdAt = :cursorTime and block.blocked.id < :cursorUserId))
            order by block.createdAt desc, block.blocked.id desc
            """)
    List<UserBlock> findBlockedUsersAfter(
            @Param("userId") UUID userId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable
    );
}
