package com.scriptles.cabinet.profile.repository;

import com.scriptles.cabinet.profile.entity.HQListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HQListItemRepository extends JpaRepository<HQListItem, UUID> {
    List<HQListItem> findByListIdOrderByPositionAsc(UUID listId);
    boolean existsByListIdAndMediaId(UUID listId, UUID mediaId);
    Optional<HQListItem> findByIdAndListId(UUID id, UUID listId);
    long countByListId(UUID listId);
    Optional<HQListItem> findTopByListIdOrderByPositionDesc(UUID listId);
}
