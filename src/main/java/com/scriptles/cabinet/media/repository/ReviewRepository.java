package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    @EntityGraph(attributePaths = {"user", "media"})
    Optional<Review> findByUserIdAndMediaId(
            UUID userId,
            UUID mediaId
    );

    @EntityGraph(attributePaths = {"user", "media"})
    Page<Review> findByMediaIdAndVisibility(
            UUID mediaId,
            Visibility visibility,
            Pageable pageable
    );

    @Query("""
            select r.media.id as mediaId,
                   avg(r.rating) as averageRating,
                   count(r.id) as ratingCount
            from Review r
            where r.visibility = :visibility
              and r.media.id in :mediaIds
            group by r.media.id
            """)
    List<MediaRatingProjection> summarizeRatings(
            @Param("mediaIds") Collection<UUID> mediaIds,
            @Param("visibility") Visibility visibility
    );

    @Query("""
            select r.rating as rating,
                   count(r.id) as ratingCount
            from Review r
            where r.media.id = :mediaId
              and r.visibility = :visibility
            group by r.rating
            order by r.rating
            """)
    List<RatingDistributionProjection> ratingDistribution(
            @Param("mediaId") UUID mediaId,
            @Param("visibility") Visibility visibility
    );

    @Query("""
            select r.media as media,
                   avg(r.rating) as averageRating,
                   count(r.id) as ratingCount
            from Review r
            where r.visibility = :visibility
              and r.media.type in :types
              and (
                    lower(r.media.title) like lower(concat('%', :query, '%'))
                    or lower(coalesce(r.media.originalTitle, '')) like lower(concat('%', :query, '%'))
                  )
              and exists (
                    select reference.id
                    from ExternalReference reference
                    where reference.media = r.media
                      and reference.primaryReference = true
                  )
            group by r.media
            order by avg(r.rating) desc,
                     count(r.id) desc,
                     lower(r.media.title) asc,
                     r.media.id asc
            """)
    Slice<RatedMediaProjection> searchRatedMedia(
            @Param("query") String query,
            @Param("types") Set<MediaType> types,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    interface MediaRatingProjection {
        UUID getMediaId();

        Double getAverageRating();

        long getRatingCount();
    }

    interface RatedMediaProjection {
        Media getMedia();

        Double getAverageRating();

        long getRatingCount();
    }

    interface RatingDistributionProjection {
        BigDecimal getRating();

        long getRatingCount();
    }
}
