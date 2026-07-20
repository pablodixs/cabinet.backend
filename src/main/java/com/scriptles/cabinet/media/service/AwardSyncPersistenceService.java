package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.AwardSyncState;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.AwardSyncStatus;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.AwardEntryRepository;
import com.scriptles.cabinet.media.repository.AwardSyncStateRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AwardSyncPersistenceService {
    private final MediaRepository mediaRepository;
    private final PersonRepository personRepository;
    private final AwardEntryRepository awardEntryRepository;
    private final AwardSyncStateRepository syncStateRepository;

    @Transactional
    public void replaceMedia(UUID mediaId, List<WikidataClient.WikidataAward> rawAwards, Duration ttl) {
        Media media = mediaRepository.getReferenceById(mediaId);
        replace(media, null, deduplicate(rawAwards));
        updateState(media, null, rawAwards.isEmpty() ? AwardSyncStatus.EMPTY : AwardSyncStatus.READY, ttl, null);
    }

    @Transactional
    public void replacePerson(UUID personId, List<WikidataClient.WikidataAward> rawAwards, Duration ttl) {
        Person person = personRepository.getReferenceById(personId);
        replace(null, person, deduplicate(rawAwards));
        updateState(null, person, rawAwards.isEmpty() ? AwardSyncStatus.EMPTY : AwardSyncStatus.READY, ttl, null);
    }

    @Transactional
    public void markMedia(UUID mediaId, AwardSyncStatus status, String errorCode, Duration ttl) {
        updateState(mediaRepository.getReferenceById(mediaId), null, status, ttl, errorCode);
    }

    @Transactional
    public void markPerson(UUID personId, AwardSyncStatus status, String errorCode, Duration ttl) {
        updateState(null, personRepository.getReferenceById(personId), status, ttl, errorCode);
    }

    private void replace(Media media, Person person, List<WikidataClient.WikidataAward> awards) {
        UUID subjectId = media != null ? media.getId() : person.getId();
        List<AwardEntry> automatic = media != null
                ? awardEntryRepository.findAllByMediaIdAndOriginAndCuratedFalse(subjectId, AwardOrigin.WIKIDATA)
                : awardEntryRepository.findAllByPersonIdAndOriginAndCuratedFalse(subjectId, AwardOrigin.WIKIDATA);
        Map<String, AwardEntry> automaticByStatement = automatic.stream()
                .filter(entry -> entry.getSourceStatementId() != null)
                .collect(Collectors.toMap(AwardEntry::getSourceStatementId, entry -> entry));
        Set<String> returnedStatements = awards.stream()
                .map(WikidataClient.WikidataAward::statementId)
                .collect(Collectors.toSet());

        List<AwardEntry> updated = new ArrayList<>();
        for (WikidataClient.WikidataAward award : awards) {
            AwardEntry entry = awardEntryRepository.findBySourceStatementId(award.statementId()).orElse(null);
            if (entry != null && entry.isCurated()) {
                continue;
            }
            if (entry == null) {
                entry = automaticByStatement.getOrDefault(award.statementId(), new AwardEntry());
                entry.setMedia(media);
                entry.setPerson(person);
                entry.setOrigin(AwardOrigin.WIKIDATA);
                entry.setSourceStatementId(award.statementId());
                entry.setCurated(false);
                entry.setHidden(false);
            }
            apply(entry, award);
            updated.add(entry);
        }
        awardEntryRepository.saveAll(updated);

        List<AwardEntry> removed = automatic.stream()
                .filter(entry -> !returnedStatements.contains(entry.getSourceStatementId()))
                .toList();
        awardEntryRepository.deleteAll(removed);
    }

    private void apply(AwardEntry entry, WikidataClient.WikidataAward award) {
        entry.setResult(award.result());
        entry.setProgramQid(award.programQid());
        entry.setProgramName(award.programName());
        entry.setCategoryQid(award.categoryQid());
        entry.setCategoryName(award.categoryName());
        entry.setCeremonyQid(award.ceremonyQid());
        entry.setCeremonyName(award.ceremonyName());
        entry.setEventDate(award.eventDate());
        entry.setEventYear(award.eventYear());
        entry.setDatePrecision(award.datePrecision());
        entry.setWorkQid(award.workQid());
        entry.setWorkName(award.workName());
        entry.setWorkMedia(resolveWorkMedia(award.workQid()));
        entry.setSourceUrl(award.sourceUrl());
    }

    private Media resolveWorkMedia(String wikidataId) {
        if (wikidataId == null) return null;
        return mediaRepository.findFirstByWikidataId(wikidataId)
                .or(() -> mediaRepository.findFirstByCanonicalBookWorkWikidataId(wikidataId))
                .orElse(null);
    }

    private List<WikidataClient.WikidataAward> deduplicate(List<WikidataClient.WikidataAward> awards) {
        Map<String, WikidataClient.WikidataAward> deduplicated = new LinkedHashMap<>();
        for (WikidataClient.WikidataAward award : awards) {
            String key = duplicateKey(award);
            deduplicated.merge(key, award, (current, candidate) ->
                    candidate.result() == AwardResult.WIN ? candidate : current);
        }
        return List.copyOf(deduplicated.values());
    }

    private String duplicateKey(WikidataClient.WikidataAward award) {
        boolean hasEventIdentity = award.ceremonyQid() != null
                || award.eventYear() != null
                || award.workQid() != null;
        if (!hasEventIdentity) {
            return award.statementId();
        }
        return String.join("|",
                Objects.toString(award.categoryQid(), ""),
                Objects.toString(award.ceremonyQid(), ""),
                Objects.toString(award.eventYear(), ""),
                Objects.toString(award.workQid(), ""));
    }

    private void updateState(
            Media media,
            Person person,
            AwardSyncStatus status,
            Duration ttl,
            String errorCode
    ) {
        UUID subjectId = media != null ? media.getId() : person.getId();
        AwardSyncState state = media != null
                ? syncStateRepository.findByMediaId(subjectId).orElseGet(AwardSyncState::new)
                : syncStateRepository.findByPersonId(subjectId).orElseGet(AwardSyncState::new);
        state.setMedia(media);
        state.setPerson(person);
        state.setStatus(status);
        state.setErrorCode(errorCode);
        Instant now = Instant.now();
        if (status != AwardSyncStatus.ERROR || state.getFetchedAt() == null) {
            state.setFetchedAt(now);
        }
        state.setExpiresAt(now.plus(ttl));
        syncStateRepository.save(state);
    }
}
