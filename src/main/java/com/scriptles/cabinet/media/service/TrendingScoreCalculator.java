package com.scriptles.cabinet.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Defines the shared weights and exponential time decay used by trending queries.
 * Daily signal counts are stored in media_ranking_snapshot; PostgreSQL applies
 * this expression before sorting so a request only loads the requested media.
 */
@Component
public class TrendingScoreCalculator {
    private static final double DECAY_BASE = 0.5;
    private static final String SQL_SCORE_EXPRESSION = """
            sum((
                snapshot.rating_activity * :ratingWeight
                + snapshot.like_activity * :likeWeight
                + snapshot.completion_activity * :completionWeight
                + snapshot.log_activity * :logWeight
                + snapshot.list_addition_activity * :listAdditionWeight
                + snapshot.review_activity * :reviewWeight
            ) * power(
                :decayBase,
                greatest(
                    ((current_timestamp at time zone 'UTC')::date - snapshot.activity_day),
                    0
                )::double precision / :halfLifeDays
            ))
            """;

    private final double ratingWeight;
    private final double likeWeight;
    private final double completionWeight;
    private final double logWeight;
    private final double listAdditionWeight;
    private final double reviewWeight;
    private final double halfLifeDays;

    public TrendingScoreCalculator(
            @Value("${media.trending.score.rating-weight:3.0}") double ratingWeight,
            @Value("${media.trending.score.like-weight:2.0}") double likeWeight,
            @Value("${media.trending.score.completion-weight:2.0}") double completionWeight,
            @Value("${media.trending.score.log-weight:1.5}") double logWeight,
            @Value("${media.trending.score.list-addition-weight:1.0}") double listAdditionWeight,
            @Value("${media.trending.score.review-weight:2.0}") double reviewWeight,
            @Value("${media.trending.score.half-life:3d}") Duration halfLife
    ) {
        if (halfLife.isZero() || halfLife.isNegative()) {
            throw new IllegalArgumentException("Trending score half-life must be positive");
        }
        this.ratingWeight = nonNegative("rating", ratingWeight);
        this.likeWeight = nonNegative("like", likeWeight);
        this.completionWeight = nonNegative("completion", completionWeight);
        this.logWeight = nonNegative("log", logWeight);
        this.listAdditionWeight = nonNegative("list addition", listAdditionWeight);
        this.reviewWeight = nonNegative("review", reviewWeight);
        this.halfLifeDays = (double) halfLife.toMillis() / Duration.ofDays(1).toMillis();
    }

    public String sqlScoreExpression() {
        return SQL_SCORE_EXPRESSION;
    }

    public MapSqlParameterSource parameters(int periodDays) {
        return new MapSqlParameterSource()
                .addValue("periodDays", periodDays)
                .addValue("ratingWeight", ratingWeight)
                .addValue("likeWeight", likeWeight)
                .addValue("completionWeight", completionWeight)
                .addValue("logWeight", logWeight)
                .addValue("listAdditionWeight", listAdditionWeight)
                .addValue("reviewWeight", reviewWeight)
                .addValue("decayBase", DECAY_BASE)
                .addValue("halfLifeDays", halfLifeDays);
    }

    private double nonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Trending score " + name + " weight must be non-negative");
        }
        return value;
    }
}
