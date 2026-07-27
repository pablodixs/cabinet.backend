package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.entity.UserMediaTag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserMediaTagRepository extends JpaRepository<UserMediaTag, UUID> {
    @EntityGraph(attributePaths = "tag")
    List<UserMediaTag> findAllByTagUserIdAndMediaIdOrderByTagNameAsc(
            UUID userId, UUID mediaId);

    @Modifying
    @Query("""
            delete from UserMediaTag mediaTag
            where mediaTag.tag.user.id = :userId
              and mediaTag.media.id = :mediaId
            """)
    void deleteAllByUserIdAndMediaId(
            @Param("userId") UUID userId,
            @Param("mediaId") UUID mediaId);

    @Query("""
            select mediaTag.tag.id as tagId, count(mediaTag.id) as usageCount
            from UserMediaTag mediaTag
            where mediaTag.tag.user.id = :userId
            group by mediaTag.tag.id
            """)
    List<TagUsageCount> countUsageByUserId(@Param("userId") UUID userId);

    @Query("""
            select distinct mediaTag.media
            from UserMediaTag mediaTag
            where mediaTag.tag.user.id = :userId
              and mediaTag.tag.normalizedName = :normalizedName
            """)
    List<Media> findTaggedMedia(
            @Param("userId") UUID userId,
            @Param("normalizedName") String normalizedName);

    interface TagUsageCount {
        UUID getTagId();
        long getUsageCount();
    }
}
