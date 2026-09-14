package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.profile.entity.CatalogOrganization;
import com.scriptles.cabinet.profile.entity.MediaOrganization;
import com.scriptles.cabinet.profile.entity.OrganizationExternalReference;
import com.scriptles.cabinet.profile.enums.OrganizationRelationship;
import com.scriptles.cabinet.profile.enums.OrganizationType;
import com.scriptles.cabinet.profile.repository.CatalogOrganizationRepository;
import com.scriptles.cabinet.profile.repository.MediaOrganizationRepository;
import com.scriptles.cabinet.profile.repository.OrganizationExternalReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogOrganizationDiscoveryService {
    private final CatalogOrganizationRepository organizations;
    private final OrganizationExternalReferenceRepository references;
    private final MediaOrganizationRepository mediaOrganizations;
    private final TmdbClient tmdb;
    private final MusicBrainzClient musicBrainz;

    @Transactional(readOnly = true)
    public boolean hasLinks(Media media) {
        return media != null && media.getId() != null && mediaOrganizations.existsByMediaId(media.getId());
    }

    @Transactional
    public void discover(Media media, ExternalSource source, String externalId) {
        if (media == null || externalId == null || source == null) return;
        try {
            if (source == ExternalSource.TMDB && (media.getType() == MediaType.MOVIE || media.getType() == MediaType.SERIES)) {
                tmdb.findOrganizationCredits(media.getType(), externalId).forEach(c -> upsert(media, source, c.externalId(), c.externalUrl(), c.name(), c.countryCode(), c.logoUrl(),
                        c.kind() == TmdbClient.OrganizationCredit.Kind.NETWORK ? OrganizationType.NETWORK : OrganizationType.PRODUCTION_COMPANY,
                        c.kind() == TmdbClient.OrganizationCredit.Kind.NETWORK ? OrganizationRelationship.NETWORK : OrganizationRelationship.PRODUCTION_COMPANY));
            } else if (source == ExternalSource.MUSICBRAINZ && media.getType() == MediaType.ALBUM) {
                musicBrainz.findOrganizationCredits(externalId).forEach(c -> upsert(media, source, c.externalId(), c.externalUrl(), c.name(), c.countryCode(), null,
                        OrganizationType.RECORD_LABEL, OrganizationRelationship.RECORD_LABEL));
            }
        } catch (ExternalMediaException ex) {
            log.warn("Organization enrichment failed for {}:{}; media remains available", source, externalId, ex);
        }
    }

    private void upsert(Media media, ExternalSource source, String externalId, String url, String name, String country,
                         String logo, OrganizationType type, OrganizationRelationship relationship) {
        CatalogOrganization organization = references.findBySourceAndExternalId(source.name(), externalId)
                .map(OrganizationExternalReference::getOrganization)
                .orElseGet(() -> organizations.findByPrimarySourceAndPrimaryExternalId(source.name(), externalId).orElseGet(CatalogOrganization::new));
        if (organization.getPrimarySource() == null) organization.setPrimarySource(source.name());
        if (organization.getPrimaryExternalId() == null) organization.setPrimaryExternalId(externalId);
        if (organization.getCanonicalName() == null) organization.setCanonicalName(name);
        if (organization.getCountryCode() == null) organization.setCountryCode(country);
        if (organization.getLogoUrl() == null) organization.setLogoUrl(logo);
        if (organization.getType() == null) organization.setType(type);
        if (organization.getWebsiteUrl() == null) organization.setWebsiteUrl(url);
        organization.setLastSyncedAt(Instant.now());
        organization = organizations.save(organization);
        OrganizationExternalReference ref = references.findBySourceAndExternalId(source.name(), externalId).orElseGet(OrganizationExternalReference::new);
        ref.setOrganization(organization); ref.setSource(source.name()); ref.setExternalId(externalId); ref.setExternalUrl(url); references.save(ref);
        if (!mediaOrganizations.existsByMediaIdAndOrganizationIdAndRelationship(media.getId(), organization.getId(), relationship)) {
            MediaOrganization link = new MediaOrganization(); link.setMedia(media); link.setOrganization(organization); link.setRelationship(relationship); mediaOrganizations.save(link);
        }
    }
}
