package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.entity.PersonExternalReference;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Resolves a person's provider identity, including legacy Person fields. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonExternalIdentityResolver {
    private final PersonRepository personRepository;
    private final PersonExternalReferenceRepository referenceRepository;

    public Optional<String> findExternalId(UUID personId, ExternalSource source) {
        if (personId == null || source == null) {
            return Optional.empty();
        }

        Optional<String> reference = referenceRepository
                .findFirstByPersonIdAndSource(personId, source)
                .map(PersonExternalReference::getExternalId)
                .filter(this::hasText);
        if (reference.isPresent()) {
            return reference;
        }

        // Be tolerant of older data and of multiple references for a provider.
        reference = referenceRepository.findAllByPersonId(personId).stream()
                .filter(value -> value.getSource() == source)
                .map(PersonExternalReference::getExternalId)
                .filter(this::hasText)
                .sorted()
                .findFirst();
        if (reference.isPresent()) {
            return reference;
        }

        return personRepository.findById(personId)
                .filter(person -> person.getExternalSource() == source)
                .map(Person::getExternalId)
                .filter(this::hasText);
    }

    public Optional<String> findExternalId(Person person, MediaCredit credit, ExternalSource source) {
        if (person == null || source == null) {
            return Optional.empty();
        }
        Optional<String> resolved = findExternalId(person.getId(), source);
        if (resolved.isPresent()) {
            return resolved;
        }
        if (credit != null && credit.getSource() == source && hasText(credit.getExternalId())) {
            return Optional.of(credit.getExternalId());
        }
        return person.getExternalSource() == source && hasText(person.getExternalId())
                ? Optional.of(person.getExternalId())
                : Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
