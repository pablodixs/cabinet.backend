package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.CreditHeadlineProjection;
import com.scriptles.cabinet.media.repository.CreditDetailsProjection;
import com.scriptles.cabinet.catalog.service.RollingCatalogMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaCreditService {
    private static final int MAX_IDENTITY_ENRICHMENTS_PER_IMPORT = 12;

    private final PersonIdentityService personIdentityService;
    private final MediaCreditRepository mediaCreditRepository;
    private final RollingCatalogMetrics rollingCatalogMetrics;
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void save(Media media, List<ExternalMedia.ExternalCredit> externalCredits) {
        save(media, externalCredits, true);
    }

    @Transactional
    public void saveWithoutIdentityEnrichment(
            Media media,
            List<ExternalMedia.ExternalCredit> externalCredits
    ) {
        save(media, externalCredits, false);
    }

    private void save(
            Media media,
            List<ExternalMedia.ExternalCredit> externalCredits,
            boolean enrichIdentities
    ) {
        if (externalCredits == null || externalCredits.isEmpty()) {
            return;
        }

        mediaCreditRepository.deleteAllByMediaId(media.getId());
        mediaCreditRepository.flush();
        Set<IdentityKey> enrichmentKeys = enrichIdentities
                ? enrichmentKeys(externalCredits)
                : Set.of();
        PersonIdentityService.ExternalPeopleSnapshot preloaded =
                personIdentityService.preloadExternalPeople(externalCredits);
        Set<CreditKey> savedCredits = new LinkedHashSet<>();
        Set<UUID> resolvedPeople = new LinkedHashSet<>();
        List<MediaCredit> credits = new ArrayList<>();
        for (ExternalMedia.ExternalCredit external : externalCredits) {
            if (!valid(external)) {
                continue;
            }
            IdentityKey identityKey = identityKey(external);
            Person person = personIdentityService.resolve(
                    external, enrichmentKeys.contains(identityKey), preloaded);
            resolvedPeople.add(person.getId());
            CreditKey key = new CreditKey(person.getId(), external.role(), normalizeText(external.characterName()));
            if (!savedCredits.add(key)) {
                continue;
            }

            MediaCredit credit = new MediaCredit();
            credit.setMedia(media);
            credit.setPerson(person);
            credit.setRole(external.role());
            credit.setCharacterName(external.characterName());
            credit.setSource(external.source());
            credit.setExternalId(blankToNull(external.externalId()));
            credit.setPosition(external.position());
            credits.add(credit);
        }
        mediaCreditRepository.saveAll(credits);
        recordImportMetrics(credits.size(), resolvedPeople.size());
    }

    private void recordImportMetrics(int credits, int people) {
        Runnable record = () -> {
            rollingCatalogMetrics.creditsPersisted(credits);
            rollingCatalogMetrics.peopleResolved(people);
            if (meterRegistry != null) {
                meterRegistry.counter("cabinet.catalog.credits.persisted").increment(credits);
                meterRegistry.counter("cabinet.catalog.people.resolved").increment(people);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    @Transactional
    public void reconcile(Media media) {
        List<MediaCredit> storedCredits = mediaCreditRepository
                .findAllByMediaIdInOrderByPositionAsc(List.of(media.getId()));
        if (storedCredits.isEmpty()) {
            return;
        }

        List<StoredCredit> snapshots = storedCredits.stream()
                .map(credit -> new StoredCredit(credit.getId(), toExternalCredit(credit)))
                .toList();
        Set<IdentityKey> enrichmentKeys = enrichmentKeys(
                snapshots.stream().map(StoredCredit::external).toList());
        PersonIdentityService.ExternalPeopleSnapshot preloaded = personIdentityService
                .preloadExternalPeople(snapshots.stream().map(StoredCredit::external).toList());
        Map<UUID, Person> resolvedPeople = new LinkedHashMap<>();
        for (StoredCredit snapshot : snapshots) {
            ExternalMedia.ExternalCredit external = snapshot.external();
            resolvedPeople.put(
                    snapshot.creditId(),
                    personIdentityService.resolve(
                            external, enrichmentKeys.contains(identityKey(external)), preloaded)
            );
        }

        Set<CreditKey> savedCredits = new LinkedHashSet<>();
        List<MediaCredit> reconciled = mediaCreditRepository
                .findAllByMediaIdInOrderByPositionAsc(List.of(media.getId()));
        for (MediaCredit credit : reconciled) {
            Person resolved = resolvedPeople.get(credit.getId());
            if (resolved != null) {
                credit.setPerson(resolved);
            }
            ExternalSource source = credit.getSource() != null
                    ? credit.getSource()
                    : credit.getPerson().getExternalSource();
            String externalId = credit.getExternalId() != null
                    ? credit.getExternalId()
                    : credit.getPerson().getExternalId();
            credit.setSource(source);
            credit.setExternalId(externalId);

            CreditKey key = new CreditKey(
                    credit.getPerson().getId(),
                    credit.getRole(),
                    normalizeText(credit.getCharacterName())
            );
            if (savedCredits.add(key)) {
                mediaCreditRepository.save(credit);
            } else {
                mediaCreditRepository.delete(credit);
            }
        }
        mediaCreditRepository.flush();
    }

    public CreditSummary summary(Media media) {
        return summaries(List.of(media)).getOrDefault(media.getId(), CreditSummary.empty());
    }

    public Page<CreditView> findByRole(UUID mediaId, CreditRole role, int page, int limit) {
        return mediaCreditRepository.findCreditDetailsByRole(
                mediaId,
                role,
                PageRequest.of(page, limit)
        ).map(this::toView);
    }

    public Map<UUID, CreditSummary> summaries(Collection<Media> mediaItems) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Media> mediaById = mediaItems.stream()
                .filter(media -> media != null && media.getId() != null)
                .collect(Collectors.toMap(
                        Media::getId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        if (mediaById.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<CreditHeadlineProjection>> creditsByMedia = new LinkedHashMap<>();
        List<UUID> mediaIds = List.copyOf(mediaById.keySet());
        List<CreditRole> headlineRoles = List.of(
                CreditRole.AUTHOR, CreditRole.CREATOR, CreditRole.DIRECTOR, CreditRole.ARTIST);
        for (int start = 0; start < mediaIds.size(); start += 100) {
            mediaCreditRepository.findHeadlines(
                    mediaIds.subList(start, Math.min(start + 100, mediaIds.size())), headlineRoles)
                    .forEach(credit -> creditsByMedia.computeIfAbsent(
                            credit.getMediaId(), ignored -> new ArrayList<>()).add(credit));
        }

        Map<UUID, CreditSummary> summaries = new LinkedHashMap<>();
        mediaById.forEach((mediaId, media) -> {
            List<CreditHeadlineProjection> credits = creditsByMedia.getOrDefault(mediaId, List.of());
            String director = namesForRole(credits, CreditRole.DIRECTOR);
            String creator = namesForRole(credits, creatorRole(media.getType()));
            summaries.put(mediaId, new CreditSummary(creator, director, List.of()));
        });
        return Map.copyOf(summaries);
    }

    private CreditView toView(MediaCredit credit) {
        Person person = credit.getPerson();
        return new CreditView(
                person.getId(),
                person.getName(),
                credit.getRole(),
                credit.getCharacterName(),
                credit.getPosition(),
                person.getImageUrl(),
                credit.getSource() != null ? credit.getSource() : person.getExternalSource(),
                credit.getExternalId() != null ? credit.getExternalId() : person.getExternalId()
        );
    }

    private CreditView toView(CreditDetailsProjection credit) {
        return new CreditView(
                credit.getPersonId(), credit.getName(), credit.getRole(), credit.getCharacterName(),
                credit.getPosition(), credit.getImageUrl(), credit.getSource(), credit.getExternalId());
    }

    private ExternalMedia.ExternalCredit toExternalCredit(MediaCredit credit) {
        Person person = credit.getPerson();
        return new ExternalMedia.ExternalCredit(
                credit.getExternalId() != null ? credit.getExternalId() : person.getExternalId(),
                person.getName(),
                credit.getRole(),
                credit.getCharacterName(),
                credit.getPosition(),
                person.getImageUrl(),
                credit.getSource() != null ? credit.getSource() : person.getExternalSource()
        );
    }

    private Set<IdentityKey> enrichmentKeys(List<ExternalMedia.ExternalCredit> externalCredits) {
        return externalCredits.stream()
                .filter(this::valid)
                .filter(credit -> credit.externalId() != null && !credit.externalId().isBlank())
                .filter(credit -> credit.source() == ExternalSource.TMDB
                        || credit.source() == ExternalSource.MUSICBRAINZ)
                .sorted(Comparator
                        .comparingInt((ExternalMedia.ExternalCredit credit) -> rolePriority(credit.role()))
                        .thenComparing(ExternalMedia.ExternalCredit::position,
                                Comparator.nullsLast(Integer::compareTo)))
                .map(this::identityKey)
                .distinct()
                .limit(MAX_IDENTITY_ENRICHMENTS_PER_IMPORT)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int rolePriority(CreditRole role) {
        return switch (role) {
            case COMPOSER -> 0;
            case DIRECTOR -> 1;
            case CREATOR -> 2;
            case ARTIST -> 3;
            case FEATURED_ARTIST -> 4;
            case AUTHOR -> 5;
            case SCREENWRITER -> 6;
            case PRODUCER -> 7;
            case ACTOR -> 8;
        };
    }

    private IdentityKey identityKey(ExternalMedia.ExternalCredit external) {
        return new IdentityKey(external.source(), blankToNull(external.externalId()));
    }

    private boolean valid(ExternalMedia.ExternalCredit external) {
        return external != null && external.name() != null && !external.name().isBlank()
                && external.role() != null && external.source() != null;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private CreditRole creatorRole(MediaType type) {
        return switch (type) {
            case BOOK -> CreditRole.AUTHOR;
            case EPISODE -> CreditRole.CREATOR;
            case MOVIE -> CreditRole.DIRECTOR;
            case SERIES -> CreditRole.CREATOR;
            case TRACK, ALBUM -> CreditRole.ARTIST;
        };
    }

    private String namesForRole(List<CreditView> credits, CreditRole role) {
        if (role == null) {
            return null;
        }
        String names = credits.stream()
                .filter(credit -> credit.role() == role)
                .map(CreditView::name)
                .distinct()
                .collect(Collectors.joining(", "));
        return names.isBlank() ? null : names;
    }

    private String namesForRole(Collection<CreditHeadlineProjection> credits, CreditRole role) {
        if (role == null) return null;
        String names = credits.stream()
                .filter(credit -> credit.getRole() == role)
                .map(CreditHeadlineProjection::getPersonName)
                .distinct()
                .collect(Collectors.joining(", "));
        return names.isBlank() ? null : names;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record CreditKey(
            UUID personId,
            CreditRole role,
            String characterName
    ) {
    }

    private record IdentityKey(ExternalSource source, String externalId) {
    }

    private record StoredCredit(UUID creditId, ExternalMedia.ExternalCredit external) {
    }

    public record CreditSummary(String creator, String director, List<CreditView> credits) {
        public static CreditSummary empty() {
            return new CreditSummary(null, null, List.of());
        }
    }

    public record CreditView(
            UUID personId,
            String name,
            CreditRole role,
            String characterName,
            Integer position,
            String imageUrl,
            ExternalSource source,
            String externalId
    ) {
    }
}
