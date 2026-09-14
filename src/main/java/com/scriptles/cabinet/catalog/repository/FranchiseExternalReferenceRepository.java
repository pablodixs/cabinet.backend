package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.*; import com.scriptles.cabinet.media.enums.ExternalSource; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface FranchiseExternalReferenceRepository extends JpaRepository<FranchiseExternalReference,UUID> { Optional<FranchiseExternalReference> findByProviderAndExternalId(ExternalSource provider,String externalId); List<FranchiseExternalReference> findByFranchiseId(UUID franchiseId); }
