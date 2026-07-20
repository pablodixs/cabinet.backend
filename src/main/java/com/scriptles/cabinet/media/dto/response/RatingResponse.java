package com.scriptles.cabinet.media.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RatingResponse(UUID mediaId, BigDecimal rating) {}
