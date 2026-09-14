package com.scriptles.cabinet.catalog.entity;
import java.io.Serializable; import java.util.UUID; import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor public class CollectionArtistId implements Serializable { private UUID collection; private UUID artist; private String role; }
