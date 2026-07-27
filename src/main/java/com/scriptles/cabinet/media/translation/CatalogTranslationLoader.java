package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.media.enrichment.CatalogEnrichmentPersistenceService;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.MediaTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogTranslationLoader {
    private final MediaTranslationRepository translationRepository;
    private final ExternalMediaProviderRegistry providerRegistry;
    private final CatalogEnrichmentPersistenceService persistenceService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadIfMissing(
            Media media,
            ExternalReference primaryReference,
            String requestedLocale
    ) {
        if (requestedLocale.equals(media.getDefaultLocale())
                || primaryReference == null
                || translationRepository.existsByMediaIdAndLocale(media.getId(), requestedLocale)) {
            return;
        }

        try {
            providerRegistry.get(primaryReference.getSource(), media.getType())
                    .findCoreById(media.getType(), primaryReference.getExternalId(), requestedLocale)
                    .ifPresent(translation -> persistenceService.saveTranslation(
                            media.getId(),
                            translation,
                            requestedLocale
                    ));
        } catch (RuntimeException failure) {
            log.warn(
                    "Translation {} for media {} could not be loaded: {}",
                    requestedLocale,
                    media.getId(),
                    failure.getMessage()
            );
        }
    }
}
