package com.scriptles.cabinet.catalog.entity;
import java.io.Serializable; import java.util.UUID; import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor public class FranchiseMediaId implements Serializable { private UUID franchise; private UUID media; }
