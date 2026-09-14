package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface CollectionItemRepository extends JpaRepository<CollectionItem,UUID> { List<CollectionItem> findByCollectionIdOrderByPositionAsc(UUID collectionId); List<CollectionItem> findByMediaId(UUID mediaId); Optional<CollectionItem> findByCollectionIdAndMediaId(UUID collectionId,UUID mediaId); }
