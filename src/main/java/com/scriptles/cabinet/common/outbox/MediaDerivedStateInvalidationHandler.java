package com.scriptles.cabinet.common.outbox;

import com.scriptles.cabinet.media.service.MediaCommunityCacheInvalidator;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MediaDerivedStateInvalidationHandler implements DomainEventHandler {
    private static final Set<DomainEventType> SUPPORTED = EnumSet.allOf(DomainEventType.class);
    private final MediaCommunityCacheInvalidator communityCacheInvalidator;
    private final CacheManager cacheManager;
    private final MediaRepository mediaRepository;

    @Override
    public Set<DomainEventType> supportedEventTypes() {
        return SUPPORTED;
    }

    @Override
    public void handle(DomainOutboxEvent event) {
        if (!"MEDIA".equals(event.getAggregateType())) {
            throw new IllegalArgumentException("Expected MEDIA aggregate for " + event.getEventType());
        }
        var media = event.getAggregateId();
        mediaRepository.findById(media).ifPresent(communityCacheInvalidator::evict);
        if (event.getEventType() == DomainEventType.MEDIA_IMPORTED
                || event.getEventType() == DomainEventType.MEDIA_METADATA_CHANGED) {
            Cache details = cacheManager.getCache("mediaDetails");
            if (details != null) {
                details.evictIfPresent(media + ":pt-BR");
                details.evictIfPresent(media + ":en-US");
            }
        }
    }
}
