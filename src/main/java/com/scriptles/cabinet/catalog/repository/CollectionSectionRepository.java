package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.CollectionSection; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CollectionSectionRepository extends JpaRepository<CollectionSection,UUID> { List<CollectionSection> findByCollectionIdOrderByPositionAsc(UUID collectionId); }
