package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class InterestScoringPolicy {
    public static final double GENRE_SHARE = 0.55;
    public static final double PERSON_SHARE = 0.35;
    public static final double RELATION_WEIGHT = 0.5;
    public static final double EXPLICIT_SCORE = 10.0;
    private static final double IMPLICIT_SEED_LIMIT = 6.0;
    private static final double INFERRED_NODE_LIMIT = 8.0;

    public double rating(BigDecimal value) {
        return value.subtract(new BigDecimal("3.0")).doubleValue() * 2.0;
    }

    public double library(UserMediaStatus status) {
        return switch (status) {
            case PLANNED -> 0.5;
            case IN_PROGRESS -> 1.5;
            case COMPLETED -> 2.0;
            case PAUSED -> 0.0;
            case DROPPED -> -2.0;
        };
    }

    public double role(CreditRole role, Integer position) {
        return switch (role) {
            case AUTHOR, CREATOR, DIRECTOR, ARTIST, FEATURED_ARTIST -> 1.0;
            case COMPOSER, SCREENWRITER -> 0.75;
            case PRODUCER -> 0.5;
            case ACTOR -> position != null && position < 10 ? 0.5 : 0.2;
        };
    }

    public double implicitSeed(double score) {
        return clamp(score, IMPLICIT_SEED_LIMIT);
    }

    public double inferredNode(double score) {
        return clamp(score, INFERRED_NODE_LIMIT);
    }

    public double explicit(InterestPreference preference) {
        return preference == InterestPreference.POSITIVE ? EXPLICIT_SCORE : -EXPLICIT_SCORE;
    }

    public double strength(double score) {
        return Math.min(1.0, Math.abs(score) / EXPLICIT_SCORE);
    }

    public String normalizeGenre(String genre) {
        return genre.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private double clamp(double value, double absoluteLimit) {
        return Math.max(-absoluteLimit, Math.min(absoluteLimit, value));
    }
}
