package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExternalMediaDetailsResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String originalTitle,
        String creator,
        String description,
        String tagline,
        String coverUrl,
        String backdropUrl,
        String logoUrl,
        String externalUrl,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        String wikidataId,
        Map<String, String> externalReferences,
        List<GenreResponse> genres,
        List<CreditResponse> credits,
        boolean imported,
        long likeCount,
        List<MediaCommunityUserResponse> recentLikers,
        Double averageRating,
        List<RatingDistributionBucket> ratingDistribution,
        long listCount,
        long completedCount,
        List<MediaCommunityUserResponse> recentCompleters,
        Object details
) {
    public record GenreResponse(String id, String name, ExternalSource source) {}
    public record CreditResponse(
            UUID personId,
            String name,
            CreditRole role,
            String characterName,
            Integer position,
            String imageUrl,
            ExternalSource source,
            String externalId
    ) {}
    public record RatingDistributionBucket(double rating, long count) {}
    public record MovieDetails(Integer runtimeMinutes, Long budget, Long revenue, String director) {}
    public record TrackDetails(Integer durationSeconds, Boolean explicit) {}
    public record AlbumDetails(String albumType, Integer numberOfTracks, String animatedCoverUrl,
                               List<TrackResponse> tracks) {}
    public record SeriesDetails(String status, Integer numberOfSeasons, Integer numberOfEpisodes,
                                LocalDate lastAirDate, List<SeasonResponse> seasons) {}
    public record BookDetails(String isbn10, String isbn13, Integer pageCount, String publisher,
                              String canonicalWorkWikidataId) {}
    public record TrackResponse(UUID id, String externalId, String title, Integer discNumber, Integer trackNumber,
                                Integer durationSeconds, Boolean explicit, Double averageRating,
                                long ratingCount, Double myRating, boolean liked) {
        public TrackResponse(UUID id, String externalId, String title, Integer discNumber, Integer trackNumber,
                              Integer durationSeconds, Boolean explicit, Double averageRating,
                              long ratingCount, Double myRating) {
            this(id, externalId, title, discNumber, trackNumber, durationSeconds, explicit,
                    averageRating, ratingCount, myRating, false);
        }
    }
    public record SeasonResponse(UUID id, String externalId, Integer seasonNumber, String name, String description,
                                 String coverUrl, Integer episodeCount, LocalDate airDate, Double averageRating,
                                 long ratingCount, Double myRating, long myRatedEpisodeCount,
                                 long eligibleEpisodeCount) {}
}
