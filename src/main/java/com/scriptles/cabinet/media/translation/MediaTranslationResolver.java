package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.media.entity.Media;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MediaTranslationResolver {
    ResolvedMediaTranslation resolve(Media media, String requestedLocale);

    Map<UUID, ResolvedMediaTranslation> resolveAll(List<Media> media, String requestedLocale);
}
