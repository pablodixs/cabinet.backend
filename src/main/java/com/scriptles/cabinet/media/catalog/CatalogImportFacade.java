package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.external.ExternalMedia;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogImportFacade {
    private final CatalogResolver resolver;
    private final CatalogImportWriter writer;

    public Result materialize(MediaTarget target) {
        CatalogResolver.Resolution resolution = resolver.resolve(target);
        ExternalMedia snapshot = resolution.snapshot() == null ? null : resolution.snapshot().media();
        try {
            return new Result(writer.materialize(target, resolution), snapshot);
        } catch (DataIntegrityViolationException race) {
            CatalogResolver.Resolution winner = resolver.resolve(target);
            if (!winner.alreadyMaterialized()) throw race;
            return new Result(writer.materialize(target, winner), snapshot);
        }
    }

    public record Result(Media media, ExternalMedia snapshot) {
    }
}
