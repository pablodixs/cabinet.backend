package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalRatingMetric;
import com.scriptles.cabinet.media.enums.ExternalSource;

public record ExternalRatingValue(
        ExternalSource source,
        ExternalRatingMetric metric,
        Double value,
        int scale,
        String displayValue
) {
}
