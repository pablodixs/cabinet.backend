package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface FranchiseCollectionRepository extends JpaRepository<FranchiseCollection,FranchiseCollectionId> { List<FranchiseCollection> findByFranchiseIdOrderByPositionAsc(UUID franchiseId); List<FranchiseCollection> findByCollectionId(UUID collectionId); }
