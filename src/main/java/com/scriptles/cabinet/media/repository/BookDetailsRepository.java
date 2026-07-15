package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.BookDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookDetailsRepository extends JpaRepository<BookDetails, UUID> {
    List<BookDetails> findAllByCanonicalWorkWikidataIdIn(Collection<String> wikidataIds);
}
