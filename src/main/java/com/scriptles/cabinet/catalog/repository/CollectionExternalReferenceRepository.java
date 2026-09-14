package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.*; import com.scriptles.cabinet.media.enums.ExternalSource; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CollectionExternalReferenceRepository extends JpaRepository<CollectionExternalReference,UUID> { Optional<CollectionExternalReference> findByProviderAndExternalId(ExternalSource provider,String externalId); List<CollectionExternalReference> findByCollectionId(UUID collectionId); }
