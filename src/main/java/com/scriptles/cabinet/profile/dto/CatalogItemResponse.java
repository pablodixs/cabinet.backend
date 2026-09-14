package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.media.enums.MediaType; import java.time.LocalDate; import java.util.UUID;
public record CatalogItemResponse(UUID id,MediaType type,String title,String coverUrl,LocalDate releaseDate) {}
