package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CollectionRepository extends JpaRepository<Collection,UUID> {
 Optional<Collection> findBySlug(String slug);
 List<Collection> findByStatusAndTitleContainingIgnoreCase(CatalogEntityStatus status,String title,Pageable pageable);
 Page<Collection> findPageByTitleContainingIgnoreCase(String title,Pageable pageable);
 Page<Collection> findByStatusAndTypeOrderByTitleAscIdAsc(CatalogEntityStatus status, CollectionType type, Pageable pageable);
 @Query(value = """
         select c.*
         from collections c
         left join collection_external_references r
           on r.collection_id = c.id and r.provider = 'TMDB'
         left join external_catalog_entities e
           on e.provider = 'TMDB' and e.entity_type = 'COLLECTION' and e.external_id = r.external_id
         where c.status = :status and c.type = :type
         order by coalesce(nullif(e.source_metadata->>'popularity', '')::double precision, 0) desc,
                  lower(c.title), c.id
         """,
         countQuery = "select count(*) from collections c where c.status = :status and c.type = :type",
         nativeQuery = true)
 Page<Collection> findByStatusAndTypeOrderByPopularityDesc(@Param("status") String status,
                                                           @Param("type") String type,
                                                           Pageable pageable);
 @Query("select c from Collection c where c.status = :status and lower(c.title) like lower(concat('%',:query,'%')) order by lower(c.title), c.id")
 List<Collection> search(@Param("query") String query,@Param("status") CatalogEntityStatus status,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Collection c where c.id = :id")
 Optional<Collection> findByIdForUpdate(@Param("id") UUID id);
}
