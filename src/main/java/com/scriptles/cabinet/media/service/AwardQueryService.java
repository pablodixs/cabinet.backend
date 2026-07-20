package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.AwardSyncState;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.AwardSectionState;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.repository.AwardEntryRepository;
import com.scriptles.cabinet.media.repository.AwardSyncStateRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AwardQueryService {
    private final MediaRepository mediaRepository;
    private final PersonRepository personRepository;
    private final AwardEntryRepository awardEntryRepository;
    private final AwardSyncStateRepository syncStateRepository;
    private final AwardRefreshScheduler refreshScheduler;

    public AwardPageResponse findMedia(UUID mediaId, AwardResult result, int page, int size) {
        if (!mediaRepository.existsById(mediaId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada");
        }
        AwardSyncState sync = syncStateRepository.findByMediaId(mediaId).orElse(null);
        Page<AwardEntry> entries = awardEntryRepository.findVisibleByMediaId(
                mediaId, result, PageRequest.of(page, size));
        long wins = awardEntryRepository.countByMediaIdAndHiddenFalseAndResult(mediaId, AwardResult.WIN);
        long nominations = awardEntryRepository.countByMediaIdAndHiddenFalseAndResult(
                mediaId, AwardResult.NOMINATION);
        if (needsRefresh(sync)) refreshScheduler.scheduleMedia(mediaId);
        return response(mediaId, AwardSubjectType.MEDIA, sync, entries, wins, nominations);
    }

    public AwardPageResponse findPerson(UUID personId, AwardResult result, int page, int size) {
        if (!personRepository.existsById(personId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Pessoa não encontrada");
        }
        AwardSyncState sync = syncStateRepository.findByPersonId(personId).orElse(null);
        Page<AwardEntry> entries = awardEntryRepository.findVisibleByPersonId(
                personId, result, PageRequest.of(page, size));
        long wins = awardEntryRepository.countByPersonIdAndHiddenFalseAndResult(personId, AwardResult.WIN);
        long nominations = awardEntryRepository.countByPersonIdAndHiddenFalseAndResult(
                personId, AwardResult.NOMINATION);
        if (needsRefresh(sync)) refreshScheduler.schedulePerson(personId);
        return response(personId, AwardSubjectType.PERSON, sync, entries, wins, nominations);
    }

    private AwardPageResponse response(
            UUID subjectId,
            AwardSubjectType type,
            AwardSyncState sync,
            Page<AwardEntry> entries,
            long wins,
            long nominations
    ) {
        return new AwardPageResponse(
                subjectId,
                type,
                state(sync, wins + nominations > 0),
                sync == null ? null : sync.getFetchedAt(),
                sync == null ? null : sync.getExpiresAt(),
                wins,
                nominations,
                entries.getContent().stream().map(this::item).toList(),
                entries.getNumber(),
                entries.getSize(),
                entries.getTotalElements(),
                entries.getTotalPages()
        );
    }

    private AwardPageResponse.Item item(AwardEntry entry) {
        Media work = entry.getWorkMedia();
        AwardPageResponse.Work workResponse = entry.getWorkQid() == null && entry.getWorkName() == null
                ? null
                : new AwardPageResponse.Work(
                        work == null ? null : work.getId(),
                        entry.getWorkQid(),
                        work == null ? entry.getWorkName() : work.getTitle());
        return new AwardPageResponse.Item(
                entry.getId(),
                entry.getResult(),
                reference(entry.getProgramQid(), entry.getProgramName()),
                reference(entry.getCategoryQid(), entry.getCategoryName()),
                reference(entry.getCeremonyQid(), entry.getCeremonyName()),
                entry.getEventDate(),
                entry.getEventYear(),
                entry.getDatePrecision(),
                workResponse,
                entry.getOrigin(),
                entry.isCurated(),
                entry.getSourceUrl()
        );
    }

    private AwardPageResponse.Reference reference(String qid, String name) {
        return qid == null && name == null ? null : new AwardPageResponse.Reference(qid, name);
    }

    private AwardSectionState state(AwardSyncState sync, boolean hasData) {
        if (sync == null) return AwardSectionState.PENDING;
        if (needsRefresh(sync)) return hasData ? AwardSectionState.STALE : AwardSectionState.PENDING;
        return switch (sync.getStatus()) {
            case PENDING -> AwardSectionState.PENDING;
            case READY -> AwardSectionState.READY;
            case EMPTY -> AwardSectionState.EMPTY;
            case ERROR -> hasData ? AwardSectionState.STALE : AwardSectionState.ERROR;
            case NOT_LINKED -> AwardSectionState.NOT_LINKED;
        };
    }

    private boolean needsRefresh(AwardSyncState sync) {
        return sync == null || sync.getExpiresAt() == null || !sync.getExpiresAt().isAfter(Instant.now());
    }
}
