package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.entity.PersonExternalReference;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonIdentityService {
    private static final Pattern WIKIDATA_QID = Pattern.compile("Q[1-9]\\d*", Pattern.CASE_INSENSITIVE);

    private final PersonRepository personRepository;
    private final PersonExternalReferenceRepository referenceRepository;
    private final MediaCreditRepository mediaCreditRepository;
    private final ArtistIdentityEnricher identityEnricher;

    @Transactional
    public Person resolve(ExternalMedia.ExternalCredit external, boolean enrichIdentity) {
        ExternalSource source = external.source();
        String externalId = blankToNull(external.externalId());
        String name = external.name().trim();
        String wikidataId = enrichIdentity
                ? identityEnricher.findWikidataId(source, externalId)
                .map(this::normalizeWikidataId)
                .orElse(null)
                : null;

        Person sourcePerson = findSourcePerson(source, externalId, name).orElse(null);
        if (sourcePerson != null && wikidataId != null) {
            List<String> storedWikidataIds = wikidataIds(sourcePerson);
            if (!storedWikidataIds.isEmpty() && !storedWikidataIds.contains(wikidataId)) {
                wikidataId = storedWikidataIds.getFirst();
            }
        }
        Person wikidataPerson = wikidataId == null
                ? null
                : findByReference(ExternalSource.WIKIDATA, wikidataId).orElse(null);

        Person person;
        if (sourcePerson != null && wikidataPerson != null && !samePerson(sourcePerson, wikidataPerson)) {
            person = merge(sourcePerson, wikidataPerson);
        } else if (sourcePerson != null) {
            person = sourcePerson;
        } else if (wikidataPerson != null) {
            person = wikidataPerson;
        } else {
            person = new Person();
            person.setName(name);
            person.setExternalSource(source);
            person.setExternalId(externalId);
        }

        person.setName(name);
        if (hasText(external.imageUrl())) {
            person.setImageUrl(external.imageUrl().trim());
        }
        if (person.getExternalSource() == source && person.getExternalId() == null) {
            person.setExternalId(externalId);
        }
        person = personRepository.save(person);

        if (externalId != null) {
            person = linkReference(person, source, externalId);
        }
        if (wikidataId != null) {
            person = linkReference(person, ExternalSource.WIKIDATA, wikidataId);
            person = mergeVerifiedNameCandidates(person, name, wikidataId);
        }
        return personRepository.save(person);
    }

    private Optional<Person> findSourcePerson(ExternalSource source, String externalId, String name) {
        if (externalId != null) {
            Optional<Person> referenced = findByReference(source, externalId);
            if (referenced.isPresent()) {
                return referenced;
            }
            return personRepository.findByExternalSourceAndExternalId(source, externalId);
        }
        return personRepository.findFirstByExternalSourceAndNameIgnoreCase(source, name);
    }

    private Optional<Person> findByReference(ExternalSource source, String externalId) {
        return referenceRepository.findBySourceAndExternalId(source, externalId)
                .map(PersonExternalReference::getPerson);
    }

    private List<String> wikidataIds(Person person) {
        return referenceRepository.findAllByPersonId(person.getId()).stream()
                .filter(reference -> reference.getSource() == ExternalSource.WIKIDATA)
                .map(PersonExternalReference::getExternalId)
                .map(this::normalizeWikidataId)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
    }

    private Person mergeVerifiedNameCandidates(Person canonical, String name, String wikidataId) {
        List<Person> candidates = new ArrayList<>(personRepository.findAllByNameIgnoreCase(name));
        for (Person candidate : candidates) {
            if (samePerson(canonical, candidate) || !personRepository.existsById(candidate.getId())) {
                continue;
            }
            if (wikidataId.equals(resolveCandidateWikidataId(candidate).orElse(null))) {
                canonical = merge(canonical, candidate);
                canonical = linkReference(canonical, ExternalSource.WIKIDATA, wikidataId);
            }
        }
        return canonical;
    }

    private Optional<String> resolveCandidateWikidataId(Person candidate) {
        Optional<String> stored = referenceRepository.findAllByPersonId(candidate.getId()).stream()
                .filter(reference -> reference.getSource() == ExternalSource.WIKIDATA)
                .map(PersonExternalReference::getExternalId)
                .map(this::normalizeWikidataId)
                .filter(java.util.Objects::nonNull)
                .findFirst();
        if (stored.isPresent()) {
            return stored;
        }

        Set<ExternalIdentity> identities = new LinkedHashSet<>();
        if (candidate.getExternalId() != null) {
            identities.add(new ExternalIdentity(candidate.getExternalSource(), candidate.getExternalId()));
        }
        referenceRepository.findAllByPersonId(candidate.getId()).stream()
                .filter(reference -> reference.getSource() != ExternalSource.WIKIDATA)
                .map(reference -> new ExternalIdentity(reference.getSource(), reference.getExternalId()))
                .forEach(identities::add);

        for (ExternalIdentity identity : identities) {
            Optional<String> resolved = identityEnricher
                    .findWikidataId(identity.source(), identity.externalId())
                    .map(this::normalizeWikidataId)
                    .filter(java.util.Objects::nonNull);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private Person linkReference(
            Person person,
            ExternalSource source,
            String externalId
    ) {
        PersonExternalReference existing = referenceRepository
                .findBySourceAndExternalId(source, externalId)
                .orElse(null);
        if (existing != null) {
            Person canonical = samePerson(person, existing.getPerson())
                    ? person
                    : merge(person, existing.getPerson());
            if (!hasText(existing.getExternalUrl())) {
                existing.setExternalUrl(externalUrl(source, externalId));
                referenceRepository.save(existing);
            }
            return canonical;
        }

        PersonExternalReference reference = new PersonExternalReference();
        reference.setPerson(person);
        reference.setSource(source);
        reference.setExternalId(externalId);
        reference.setExternalUrl(externalUrl(source, externalId));
        referenceRepository.save(reference);
        return person;
    }

    private Person merge(Person first, Person second) {
        if (samePerson(first, second)) {
            return first;
        }
        Person winner = older(first, second);
        Person loser = samePerson(winner, first) ? second : first;

        backfillLegacyReference(winner);
        backfillLegacyReference(loser);
        mergeMetadata(winner, loser);

        List<PersonExternalReference> loserReferences = referenceRepository.findAllByPersonId(loser.getId());
        loserReferences.forEach(reference -> reference.setPerson(winner));
        referenceRepository.saveAll(loserReferences);

        List<MediaCredit> winnerCredits = mediaCreditRepository.findAllByPersonId(winner.getId());
        Set<CreditMergeKey> creditKeys = new LinkedHashSet<>();
        winnerCredits.stream().map(this::creditMergeKey).forEach(creditKeys::add);
        for (MediaCredit credit : mediaCreditRepository.findAllByPersonId(loser.getId())) {
            if (creditKeys.add(creditMergeKey(credit))) {
                credit.setPerson(winner);
                mediaCreditRepository.save(credit);
            } else {
                mediaCreditRepository.delete(credit);
            }
        }

        personRepository.save(winner);
        referenceRepository.flush();
        mediaCreditRepository.flush();
        personRepository.delete(loser);
        personRepository.flush();
        return winner;
    }

    private void backfillLegacyReference(Person person) {
        if (!hasText(person.getExternalId())) {
            return;
        }
        Optional<PersonExternalReference> existing = referenceRepository.findBySourceAndExternalId(
                person.getExternalSource(), person.getExternalId());
        if (existing.isPresent()) {
            return;
        }
        PersonExternalReference reference = new PersonExternalReference();
        reference.setPerson(person);
        reference.setSource(person.getExternalSource());
        reference.setExternalId(person.getExternalId());
        reference.setExternalUrl(externalUrl(person.getExternalSource(), person.getExternalId()));
        referenceRepository.save(reference);
    }

    private void mergeMetadata(Person winner, Person loser) {
        if (!hasText(winner.getBiography()) && hasText(loser.getBiography())) {
            winner.setBiography(loser.getBiography());
        }
        if (!hasText(winner.getImageUrl()) && hasText(loser.getImageUrl())) {
            winner.setImageUrl(loser.getImageUrl());
        }
    }

    private Person older(Person first, Person second) {
        Comparator<Person> comparator = Comparator
                .comparing(Person::getCreatedAt, Comparator.nullsLast(Instant::compareTo))
                .thenComparing(person -> person.getId().toString());
        return comparator.compare(first, second) <= 0 ? first : second;
    }

    private CreditMergeKey creditMergeKey(MediaCredit credit) {
        return new CreditMergeKey(
                credit.getMedia().getId(),
                credit.getRole(),
                normalizeText(credit.getCharacterName())
        );
    }

    private String normalizeWikidataId(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return WIKIDATA_QID.matcher(normalized).matches() ? normalized : null;
    }

    private String externalUrl(ExternalSource source, String externalId) {
        return switch (source) {
            case TMDB -> "https://www.themoviedb.org/person/" + externalId;
            case MUSICBRAINZ -> "https://musicbrainz.org/artist/" + externalId;
            case WIKIDATA -> "https://www.wikidata.org/wiki/" + externalId;
            default -> null;
        };
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean samePerson(Person first, Person second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private record ExternalIdentity(ExternalSource source, String externalId) {
    }

    private record CreditMergeKey(UUID mediaId, CreditRole role, String characterName) {
    }
}
