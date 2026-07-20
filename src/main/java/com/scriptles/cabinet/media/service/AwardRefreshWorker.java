package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.AwardSyncStatus;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwardRefreshWorker {
    private static final Duration AWARD_TTL = Duration.ofDays(7);
    private static final Duration FAILURE_RETRY = Duration.ofHours(1);

    private final MediaRepository mediaRepository;
    private final PersonRepository personRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final PersonExternalReferenceRepository personExternalReferenceRepository;
    private final WikidataClient wikidataClient;
    private final AwardSyncPersistenceService persistenceService;

    public void refreshMedia(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null) return;
        String qid = media.getWikidataId();
        if (qid == null || qid.isBlank()) {
            qid = externalReferenceRepository.findByMediaIdAndSource(mediaId, ExternalSource.WIKIDATA)
                    .map(reference -> reference.getExternalId()).orElse(null);
        }
        if (qid == null || qid.isBlank()) {
            persistenceService.markMedia(mediaId, AwardSyncStatus.NOT_LINKED, "WIKIDATA_NOT_LINKED", AWARD_TTL);
            return;
        }
        try {
            WikidataClient.WikidataAwards awards = wikidataClient.findAwards(qid);
            if (awards.incomplete()) {
                persistenceService.markMedia(mediaId, AwardSyncStatus.ERROR, "PROVIDER_UNAVAILABLE", FAILURE_RETRY);
            } else {
                persistenceService.replaceMedia(mediaId, awards.items(), AWARD_TTL);
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to refresh awards for media {}: {}", mediaId, exception.getMessage());
            persistenceService.markMedia(mediaId, AwardSyncStatus.ERROR, "PROVIDER_UNAVAILABLE", FAILURE_RETRY);
        }
    }

    public void refreshPerson(UUID personId) {
        Person person = personRepository.findById(personId).orElse(null);
        if (person == null) return;
        String qid = personExternalReferenceRepository
                .findFirstByPersonIdAndSource(personId, ExternalSource.WIKIDATA)
                .map(reference -> reference.getExternalId()).orElse(null);
        if ((qid == null || qid.isBlank()) && person.getExternalSource() == ExternalSource.WIKIDATA) {
            qid = person.getExternalId();
        }
        if (qid == null || qid.isBlank()) {
            persistenceService.markPerson(personId, AwardSyncStatus.NOT_LINKED, "WIKIDATA_NOT_LINKED", AWARD_TTL);
            return;
        }
        try {
            WikidataClient.WikidataAwards awards = wikidataClient.findAwards(qid);
            if (awards.incomplete()) {
                persistenceService.markPerson(personId, AwardSyncStatus.ERROR, "PROVIDER_UNAVAILABLE", FAILURE_RETRY);
            } else {
                persistenceService.replacePerson(personId, awards.items(), AWARD_TTL);
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to refresh awards for person {}: {}", personId, exception.getMessage());
            persistenceService.markPerson(personId, AwardSyncStatus.ERROR, "PROVIDER_UNAVAILABLE", FAILURE_RETRY);
        }
    }
}
