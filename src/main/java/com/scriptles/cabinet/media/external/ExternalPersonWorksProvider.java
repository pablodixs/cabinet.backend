package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;

import java.util.List;

public interface ExternalPersonWorksProvider {
    ExternalSource source();

    PersonWorks findPersonWorks(String personExternalId, String language);

    record PersonWorks(List<Work> items, boolean incomplete) {
    }

    record Work(
            ExternalMedia media,
            CreditRole role,
            String characterName,
            double relevance
    ) {
        /**
         * Kept for callers that only use a provider as a media catalog, such as
         * MoreByService. Person work responses should use the role-aware form.
         */
        public Work(ExternalMedia media, double relevance) {
            this(media, null, null, relevance);
        }
    }
}
