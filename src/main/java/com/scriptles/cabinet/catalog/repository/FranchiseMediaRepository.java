package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface FranchiseMediaRepository extends JpaRepository<FranchiseMedia,FranchiseMediaId> { List<FranchiseMedia> findByFranchiseId(UUID franchiseId); List<FranchiseMedia> findByMediaId(UUID mediaId); }
