package com.scriptles.cabinet.profile.repository;
import com.scriptles.cabinet.profile.entity.HQCatalogLink; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface HQCatalogLinkRepository extends JpaRepository<HQCatalogLink,UUID> { List<HQCatalogLink> findByHqProfileId(UUID hqId); List<HQCatalogLink> findByOrganizationId(UUID organizationId); }
