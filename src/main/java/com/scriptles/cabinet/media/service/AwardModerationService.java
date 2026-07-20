package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.CreateAwardRequest;
import com.scriptles.cabinet.media.dto.request.UpdateAwardRequest;
import com.scriptles.cabinet.media.dto.response.ModerationAwardResponse;
import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.AwardEntryRevision;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.repository.AwardEntryRepository;
import com.scriptles.cabinet.media.repository.AwardEntryRevisionRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AwardModerationService {
    private final AwardEntryRepository awardEntryRepository;
    private final AwardEntryRevisionRepository revisionRepository;
    private final MediaRepository mediaRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final AwardRefreshScheduler refreshScheduler;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<ModerationAwardResponse> find(
            AwardSubjectType subjectType,
            UUID subjectId,
            int page,
            int size
    ) {
        ensureSubject(subjectType, subjectId);
        Page<AwardEntry> result = subjectType == AwardSubjectType.MEDIA
                ? awardEntryRepository.findModerationByMediaId(subjectId, PageRequest.of(page, size))
                : awardEntryRepository.findModerationByPersonId(subjectId, PageRequest.of(page, size));
        return PageResponse.from(result.map(this::response));
    }

    @Transactional
    public ModerationAwardResponse create(CreateAwardRequest request, UUID editorId) {
        User editor = editor(editorId);
        AwardEntry entry = new AwardEntry();
        if (request.subjectType() == AwardSubjectType.MEDIA) {
            entry.setMedia(media(request.subjectId()));
        } else {
            entry.setPerson(person(request.subjectId()));
        }
        entry.setOrigin(AwardOrigin.MANUAL);
        entry.setCurated(true);
        entry.setCuratedBy(editor);
        entry.setHidden(false);
        apply(entry, request.result(), request.programQid(), request.programName(),
                request.categoryQid(), request.categoryName(), request.ceremonyQid(),
                request.ceremonyName(), request.eventDate(), request.eventYear(),
                request.datePrecision(), request.workQid(), request.workName(), request.sourceUrl());
        AwardEntry saved = awardEntryRepository.saveAndFlush(entry);
        revision(saved, editor, "CREATE", null, snapshot(saved));
        return response(saved);
    }

    @Transactional
    public ModerationAwardResponse update(UUID awardId, UpdateAwardRequest request, UUID editorId) {
        AwardEntry entry = entry(awardId);
        if (entry.getVersion() != request.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "AWARD_CHANGED",
                    "Esta premiação foi alterada por outra pessoa. Recarregue antes de salvar");
        }
        User editor = editor(editorId);
        String before = snapshot(entry);
        apply(entry, request.result(), request.programQid(), request.programName(),
                request.categoryQid(), request.categoryName(), request.ceremonyQid(),
                request.ceremonyName(), request.eventDate(), request.eventYear(),
                request.datePrecision(), request.workQid(), request.workName(), request.sourceUrl());
        entry.setCurated(true);
        entry.setCuratedBy(editor);
        entry.setHidden(request.hidden());
        AwardEntry saved = awardEntryRepository.saveAndFlush(entry);
        revision(saved, editor, "UPDATE", before, snapshot(saved));
        return response(saved);
    }

    @Transactional
    public void hide(UUID awardId, UUID editorId) {
        AwardEntry entry = entry(awardId);
        User editor = editor(editorId);
        String before = snapshot(entry);
        entry.setHidden(true);
        entry.setCurated(true);
        entry.setCuratedBy(editor);
        AwardEntry saved = awardEntryRepository.saveAndFlush(entry);
        revision(saved, editor, "HIDE", before, snapshot(saved));
    }

    @Transactional
    public ModerationAwardResponse resetToSource(UUID awardId, UUID editorId) {
        AwardEntry entry = entry(awardId);
        if (entry.getOrigin() != AwardOrigin.WIKIDATA || entry.getSourceStatementId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AWARD_HAS_NO_EXTERNAL_SOURCE",
                    "Somente premiações importadas do Wikidata podem ser restauradas");
        }
        User editor = editor(editorId);
        String before = snapshot(entry);
        entry.setCurated(false);
        entry.setCuratedBy(null);
        entry.setHidden(false);
        AwardEntry saved = awardEntryRepository.saveAndFlush(entry);
        revision(saved, editor, "RESET_TO_SOURCE", before, snapshot(saved));
        schedule(saved);
        return response(saved);
    }

    private void apply(
            AwardEntry entry,
            com.scriptles.cabinet.media.enums.AwardResult result,
            String programQid,
            String programName,
            String categoryQid,
            String categoryName,
            String ceremonyQid,
            String ceremonyName,
            java.time.LocalDate eventDate,
            Integer eventYear,
            com.scriptles.cabinet.media.enums.AwardDatePrecision datePrecision,
            String workQid,
            String workName,
            String sourceUrl
    ) {
        entry.setResult(result);
        entry.setProgramQid(trimToNull(programQid));
        entry.setProgramName(trimToNull(programName));
        entry.setCategoryQid(trimToNull(categoryQid));
        entry.setCategoryName(categoryName.trim());
        entry.setCeremonyQid(trimToNull(ceremonyQid));
        entry.setCeremonyName(trimToNull(ceremonyName));
        entry.setEventDate(eventDate);
        entry.setEventYear(eventYear != null ? eventYear : eventDate == null ? null : eventDate.getYear());
        entry.setDatePrecision(datePrecision != null ? datePrecision
                : eventDate == null ? null : com.scriptles.cabinet.media.enums.AwardDatePrecision.DAY);
        entry.setWorkQid(trimToNull(workQid));
        entry.setWorkName(trimToNull(workName));
        entry.setWorkMedia(resolveWorkMedia(entry.getWorkQid()));
        entry.setSourceUrl(trimToNull(sourceUrl));
    }

    private Media resolveWorkMedia(String wikidataId) {
        if (wikidataId == null) return null;
        return mediaRepository.findFirstByWikidataId(wikidataId)
                .or(() -> mediaRepository.findFirstByCanonicalBookWorkWikidataId(wikidataId))
                .orElse(null);
    }

    private void ensureSubject(AwardSubjectType type, UUID id) {
        if (type == AwardSubjectType.MEDIA) media(id); else person(id);
    }

    private Media media(UUID id) {
        return mediaRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
    }

    private Person person(UUID id) {
        return personRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Pessoa não encontrada"));
    }

    private User editor(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));
    }

    private AwardEntry entry(UUID id) {
        return awardEntryRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "AWARD_NOT_FOUND", "Premiação não encontrada"));
    }

    private void schedule(AwardEntry entry) {
        if (entry.getMedia() != null) refreshScheduler.scheduleMedia(entry.getMedia().getId());
        else refreshScheduler.schedulePerson(entry.getPerson().getId());
    }

    private void revision(AwardEntry entry, User editor, String action, String before, String after) {
        AwardEntryRevision revision = new AwardEntryRevision();
        revision.setAwardEntry(entry);
        revision.setEditedBy(editor);
        revision.setAction(action);
        revision.setBeforeState(before);
        revision.setAfterState(after);
        revisionRepository.save(revision);
    }

    private String snapshot(AwardEntry entry) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("result", entry.getResult());
        state.put("programQid", entry.getProgramQid());
        state.put("programName", entry.getProgramName());
        state.put("categoryQid", entry.getCategoryQid());
        state.put("categoryName", entry.getCategoryName());
        state.put("ceremonyQid", entry.getCeremonyQid());
        state.put("ceremonyName", entry.getCeremonyName());
        state.put("eventDate", entry.getEventDate());
        state.put("eventYear", entry.getEventYear());
        state.put("datePrecision", entry.getDatePrecision());
        state.put("workQid", entry.getWorkQid());
        state.put("workName", entry.getWorkName());
        state.put("sourceUrl", entry.getSourceUrl());
        state.put("curated", entry.isCurated());
        state.put("hidden", entry.isHidden());
        return objectMapper.writeValueAsString(state);
    }

    private ModerationAwardResponse response(AwardEntry entry) {
        AwardSubjectType type = entry.getMedia() != null ? AwardSubjectType.MEDIA : AwardSubjectType.PERSON;
        UUID subjectId = entry.getMedia() != null ? entry.getMedia().getId() : entry.getPerson().getId();
        return new ModerationAwardResponse(
                entry.getId(), type, subjectId, entry.getResult(), entry.getProgramQid(),
                entry.getProgramName(), entry.getCategoryQid(), entry.getCategoryName(),
                entry.getCeremonyQid(), entry.getCeremonyName(), entry.getEventDate(),
                entry.getEventYear(), entry.getDatePrecision(), entry.getWorkQid(),
                entry.getWorkName(), entry.getWorkMedia() == null ? null : entry.getWorkMedia().getId(),
                entry.getOrigin(), entry.getSourceStatementId(), entry.getSourceUrl(),
                entry.isCurated(), entry.isHidden(), entry.getVersion()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
