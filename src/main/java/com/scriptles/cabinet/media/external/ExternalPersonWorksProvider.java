package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;

import java.util.List;

public interface ExternalPersonWorksProvider {
    ExternalSource source();

    PersonWorks findPersonWorks(String personExternalId, String language);

    record PersonWorks(List<Work> items, boolean incomplete) {
    }

    record Work(ExternalMedia media, double relevance) {
    }
}
