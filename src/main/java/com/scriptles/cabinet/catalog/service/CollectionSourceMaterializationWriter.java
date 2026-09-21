package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionItem;
import com.scriptles.cabinet.catalog.entity.CollectionSourceItem;
import com.scriptles.cabinet.catalog.franchise.FranchiseMediaRelationType;
import com.scriptles.cabinet.catalog.repository.CollectionItemRepository;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.catalog.repository.CollectionSourceItemRepository;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionSourceMaterializationWriter {
    private final CollectionSourceItemRepository sourceItems;
    private final CollectionItemRepository collectionItems;
    private final CollectionRepository collections;
    private final MediaRepository mediaRepository;
    private final CacheManager cacheManager;

    @Transactional
    public int markResolving(UUID collectionId, String externalId) {
        List<CollectionSourceItem> items = collectionId == null
                ? sourceItems.findByProviderAndExternalIdAndSourcePresentTrue("TMDB", externalId)
                : sourceItems.findByCollectionIdAndProviderAndExternalId(collectionId, "TMDB", externalId)
                    .filter(CollectionSourceItem::isSourcePresent).stream().toList();
        Instant now = Instant.now();
        int changed = 0;
        for (CollectionSourceItem item : items) {
            if (!"RESOLVED".equals(item.getResolutionStatus())) {
                item.setResolutionStatus("RESOLVING");
                item.setUpdatedAt(now);
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public int resolveAll(String externalId, UUID mediaId) {
        Media media = mediaRepository.getReferenceById(mediaId);
        Instant now = Instant.now();
        List<CollectionSourceItem> sourceRows = sourceItems
                .findByProviderAndExternalIdAndSourcePresentTrue("TMDB", externalId);
        int resolved = 0;
        for (CollectionSourceItem source : sourceRows) {
            Collection collection = collections.findByIdForUpdate(source.getCollectionId()).orElse(null);
            if (collection == null) continue;
            CollectionItem link = collectionItems.findByCollectionIdAndMediaId(collection.getId(), mediaId)
                    .orElse(null);
            if (link == null) {
                link = new CollectionItem();
                link.setCollection(collection);
                link.setMedia(media);
                link.setPosition(source.getPosition());
                link.setRelationType(FranchiseMediaRelationType.CORE);
                link.setSourceManaged(true);
                link.setSourceProvider(ExternalSource.TMDB);
                link.setCreatedAt(now);
                link.setUpdatedAt(now);
                collectionItems.save(link);
                evictCollection(collection.getId());
            } else if (link.isSourceManaged() && link.getSourceProvider() == ExternalSource.TMDB) {
                boolean changed = link.getPosition() != source.getPosition()
                        || link.getRelationType() != FranchiseMediaRelationType.CORE;
                link.setPosition(source.getPosition());
                link.setRelationType(FranchiseMediaRelationType.CORE);
                link.setUpdatedAt(now);
                if (changed) evictCollection(collection.getId());
            }
            source.setResolvedMediaId(mediaId);
            source.setResolutionStatus("RESOLVED");
            source.setLastMaterializationError(null);
            source.setUpdatedAt(now);
            evictCollection(collection.getId());
            resolved++;
        }
        if (resolved > 0) evictMedia(mediaId);
        return resolved;
    }

    private void evictCollection(UUID id) {
        evictAfterCommit("collectionDetails", id);
    }

    private void evictMedia(UUID id) {
        evictAfterCommit("mediaDetails", id + ":pt-BR");
        evictAfterCommit("mediaDetails", id + ":en-US");
    }

    private void evictAfterCommit(String cacheName, Object key) {
        Runnable eviction = () -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.evict(key);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }
}
