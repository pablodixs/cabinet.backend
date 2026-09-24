package com.scriptles.cabinet.status;

import java.time.Instant;
import java.util.List;

public record DatabaseEfficiencyResponse(
        Instant sampledAt,
        Instant metricsStartedAt,
        Instant pgStatsResetAt,
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
        List<QueryStat> topByExecutionTime,
        List<QueryStat> topByAverageTime,
        List<SearchTiming> searchTimings,
        double poolActiveConnections,
        double poolIdleConnections,
        double poolPendingConnections
) {
    public record QueryStat(String query, long calls, long rows, double totalExecutionMs,
                            double meanExecutionMs, double maxExecutionMs) {}
    public record SearchTiming(String stage, long calls, double averageMs, double maxMs) {}
}
