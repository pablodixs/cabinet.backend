package com.scriptles.cabinet.catalog.entity;
import com.scriptles.cabinet.media.entity.Person; import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.io.Serializable;
@Entity @Table(name="collection_artists") @IdClass(CollectionArtistId.class) @Getter @Setter @NoArgsConstructor
public class CollectionArtist { @Id @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="collection_id") private Collection collection; @Id @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="artist_id") private Person artist; @Id @Column(length=50) private String role="PRIMARY"; }
