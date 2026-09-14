package com.scriptles.cabinet.catalog.repository;
import com.scriptles.cabinet.catalog.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CollectionArtistRepository extends JpaRepository<CollectionArtist,CollectionArtistId> { List<CollectionArtist> findByCollectionId(UUID collectionId); List<CollectionArtist> findByArtistId(UUID artistId); }
