package com.scriptles.cabinet.status;

import java.time.Instant;
import java.util.List;

public record DatabaseEfficiencyResponse(
        Instant sampledAt,
        Instant metricsStartedAt,
        boolean pgStatsAvailable,
        String pgStatsUnavailableReason,
        long catalogJobsPending,
        long catalogJobsProcessing,
        long catalogJobsCompletedLast24Hours,
        long mediaImportedLastHour,
        long creditsCreatedLastHour,
        long peopleResolvedLastHour,
        double mediaMaterialized,
        double creditsPersisted,
        double peopleResolved,
        double recommendationCandidates,
        double recommendationCreditsLoaded,
        double interestGraphMedia,
        double interestGraphCreditsLoaded,
        List<QueryStat> topByRows,
        List<QueryStat> topByCalls,
        List<QueryStat> topByExecutionTime
) {
    public record QueryStat(String query, long calls, long rows, double totalExecutionMs) {}
}
