package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {
    Optional<Person> findByExternalSourceAndExternalId(ExternalSource externalSource, String externalId);

    Optional<Person> findFirstByExternalSourceAndNameIgnoreCase(ExternalSource externalSource, String name);

    List<Person> findAllByNameIgnoreCase(String name);

    @Query("""
            select distinct person from Person person
            where lower(person.name) like lower(concat('%', :query, '%'))
              and (:type is null or exists (select credit.id from MediaCredit credit
                   where credit.person = person and credit.media.typeValue = :type))
            order by lower(person.name), person.id
            """)
    List<Person> findInterestOptions(
            @Param("query") String query,
            @Param("type") String type,
            Pageable pageable
    );
}
