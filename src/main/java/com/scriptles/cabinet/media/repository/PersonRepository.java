package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {
    Optional<Person> findByExternalSourceAndExternalId(ExternalSource externalSource, String externalId);

    Optional<Person> findFirstByExternalSourceAndNameIgnoreCase(ExternalSource externalSource, String name);

    List<Person> findAllByNameIgnoreCase(String name);
}
