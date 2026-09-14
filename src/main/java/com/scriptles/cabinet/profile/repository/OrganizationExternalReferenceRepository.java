package com.scriptles.cabinet.profile.repository;

import com.scriptles.cabinet.profile.entity.OrganizationExternalReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationExternalReferenceRepository extends JpaRepository<OrganizationExternalReference, UUID> {
    Optional<OrganizationExternalReference> findBySourceAndExternalId(String source, String externalId);
}
