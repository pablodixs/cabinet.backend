package com.scriptles.cabinet.profile.repository;
import com.scriptles.cabinet.profile.entity.MediaOrganization; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface MediaOrganizationRepository extends JpaRepository<MediaOrganization,UUID> {
 @Query("select mo.media from MediaOrganization mo where mo.organization.id in (select l.organization.id from HQCatalogLink l where l.hqProfile.id=:hqId) order by mo.media.releaseDate desc nulls last, lower(mo.media.title)") Page<com.scriptles.cabinet.media.entity.Media> findMediaForHq(@Param("hqId") UUID hqId,Pageable pageable);
 List<MediaOrganization> findByMediaId(UUID mediaId);
 boolean existsByMediaId(UUID mediaId);
 boolean existsByMediaIdAndOrganizationIdAndRelationship(UUID mediaId, UUID organizationId, com.scriptles.cabinet.profile.enums.OrganizationRelationship relationship);
}
