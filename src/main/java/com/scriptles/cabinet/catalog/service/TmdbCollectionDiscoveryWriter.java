package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.catalog.event.TmdbCollectionReferenceLinkedEvent;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.TmdbCollectionMembership;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TmdbCollectionDiscoveryWriter {
    private final CollectionRepository collectionRepository;
    private final CollectionExternalReferenceRepository referenceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UUID ensureLinkedCollection(TmdbCollectionMembership membership) {
        CollectionExternalReference existingReference = referenceRepository
                .findByProviderAndExternalId(ExternalSource.TMDB, membership.externalId())
                .orElse(null);
        if (existingReference != null) {
            Collection collection = existingReference.getCollection();
            if (existingReference.getLastSyncedAt() == null) {
                eventPublisher.publishEvent(new TmdbCollectionReferenceLinkedEvent(collection.getId()));
            }
            return collection.getId();
        }

        Collection collection = new Collection();
        collection.setSlug(uniqueSlug(membership.externalId()));
        collection.setTitle(membership.name());
        collection.setOriginalTitle(membership.name());
        collection.setType(CollectionType.FILM_SERIES);
        collection.setSourceMode(CollectionSourceMode.EXTERNAL);
        collection.setStatus(CatalogEntityStatus.ACTIVE);
        collection = collectionRepository.save(collection);

        CollectionExternalReference reference = new CollectionExternalReference();
        reference.setCollection(collection);
        reference.setProvider(ExternalSource.TMDB);
        reference.setExternalId(membership.externalId());
        reference.setExternalUrl("https://www.themoviedb.org/collection/" + membership.externalId());
        referenceRepository.save(reference);
        eventPublisher.publishEvent(new TmdbCollectionReferenceLinkedEvent(collection.getId()));
        return collection.getId();
    }

    private String uniqueSlug(String externalId) {
        String baseSlug = "tmdb-collection-" + externalId;
        String slug = baseSlug;
        int suffix = 2;
        while (collectionRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + suffix++;
        }
        return slug;
    }
}
