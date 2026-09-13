package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.entity.PersonExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonExternalIdentityResolverTest {
    @Mock
    private PersonRepository personRepository;
    @Mock
    private PersonExternalReferenceRepository referenceRepository;
    @InjectMocks
    private PersonExternalIdentityResolver resolver;

    @Test
    void resolvesEachProviderFromThePersonsReferences() {
        UUID personId = UUID.randomUUID();
        Person person = person(personId, ExternalSource.TMDB, "legacy-tmdb");
        PersonExternalReference tmdb = reference(person, ExternalSource.TMDB, "7467");
        PersonExternalReference musicBrainz = reference(person, ExternalSource.MUSICBRAINZ, "artist-id");

        when(referenceRepository.findFirstByPersonIdAndSource(personId, ExternalSource.TMDB))
                .thenReturn(Optional.of(tmdb));
        when(referenceRepository.findFirstByPersonIdAndSource(personId, ExternalSource.MUSICBRAINZ))
                .thenReturn(Optional.empty());
        when(referenceRepository.findAllByPersonId(personId)).thenReturn(List.of(tmdb, musicBrainz));

        assertThat(resolver.findExternalId(personId, ExternalSource.TMDB)).contains("7467");
        assertThat(resolver.findExternalId(personId, ExternalSource.MUSICBRAINZ)).contains("artist-id");
    }

    @Test
    void fallsBackToTheLegacyPersonIdentity() {
        UUID personId = UUID.randomUUID();
        Person person = person(personId, ExternalSource.TMDB, "7467");
        when(referenceRepository.findFirstByPersonIdAndSource(personId, ExternalSource.TMDB))
                .thenReturn(Optional.empty());
        when(referenceRepository.findAllByPersonId(personId)).thenReturn(List.of());
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        assertThat(resolver.findExternalId(personId, ExternalSource.TMDB)).contains("7467");
    }

    private Person person(UUID id, ExternalSource source, String externalId) {
        Person person = new Person();
        person.setId(id);
        person.setName("A Person");
        person.setExternalSource(source);
        person.setExternalId(externalId);
        return person;
    }

    private PersonExternalReference reference(Person person, ExternalSource source, String externalId) {
        PersonExternalReference reference = new PersonExternalReference();
        reference.setPerson(person);
        reference.setSource(source);
        reference.setExternalId(externalId);
        return reference;
    }
}
