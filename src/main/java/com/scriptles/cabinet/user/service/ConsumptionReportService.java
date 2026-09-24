package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.ReportRankingProjection;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportItem;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportPeriodOption;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportResponse;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportSection;
import com.scriptles.cabinet.user.entity.UserEpisodeWatch;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ConsumptionReportPeriod;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.repository.ConsumptionReportActivityRepository;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ConsumptionReportService {
    private static final int RANKING_LIMIT = 5;
    private static final EnumSet<ProfileActivityType> CONSUMPTION_TYPES = EnumSet.of(
            ProfileActivityType.COMPLETED,
            ProfileActivityType.LOGGED,
            ProfileActivityType.RELOGGED,
            ProfileActivityType.WATCHED,
            ProfileActivityType.REWATCHED,
            ProfileActivityType.MARKED_WATCHED
    );
    private static final Set<MediaType> REPORT_TYPES = Set.of(
            MediaType.BOOK, MediaType.MOVIE, MediaType.SERIES, MediaType.ALBUM
    );

    private final ConsumptionReportActivityRepository activityRepository;
    private final UserEpisodeWatchRepository episodeWatchRepository;
    private final SeriesEpisodeRepository seriesEpisodeRepository;
    private final AlbumTrackRepository albumTrackRepository;
    private final MediaRepository mediaRepository;
    private final MediaCreditRepository mediaCreditRepository;

    @Transactional(readOnly = true)
    public ConsumptionReportResponse find(
            UUID userId,
            ConsumptionReportPeriod period,
            int year,
            Integer month,
            MediaType requestedType
    ) {
        validate(period, year, month, requestedType);
        LocalDate today = CabinetTime.today();
        LocalDate selectedFrom = period == ConsumptionReportPeriod.YEAR
                ? LocalDate.of(year, 1, 1)
                : LocalDate.of(year, month, 1);
        LocalDate selectedTo = period == ConsumptionReportPeriod.YEAR
                ? selectedFrom.withMonth(12).withDayOfMonth(31)
                : selectedFrom.withDayOfMonth(selectedFrom.lengthOfMonth());
        if (selectedFrom.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_PERIOD_IN_FUTURE",
                    "O período informado está no futuro");
        }

        List<UserMediaActivity> activities = activityRepository.findAllConsumptionActivities(
                userId, CONSUMPTION_TYPES);
        List<UserEpisodeWatch> watches = episodeWatchRepository.findAllConsumptionWatches(userId);
        List<RawEvent> rawEvents = new ArrayList<>(activities.size() + watches.size());
        activities.forEach(activity -> rawEvents.add(new RawEvent(
                activity.getMedia(), activity.getOccurredOn(), activity.getType(), false)));
        watches.forEach(watch -> rawEvents.add(new RawEvent(
                watch.getEpisode().getEpisodeMedia(),
                watch.getWatchedAt().atZone(CabinetTime.ZONE).toLocalDate(),
                null,
                true)));

        Map<UUID, UUID> episodeToSeries = loadEpisodeParents(rawEvents);
        Map<UUID, UUID> trackToAlbum = loadAlbumParents(rawEvents);
        Set<EventKey> activityEpisodeKeys = rawEvents.stream()
                .filter(event -> !event.watch() && typeOf(event.media()) == MediaType.EPISODE)
                .map(event -> new EventKey(event.media().getId(), event.date()))
                .collect(Collectors.toSet());
        Set<EventKey> episodeSeriesKeys = rawEvents.stream()
                .filter(event -> typeOf(event.media()) == MediaType.EPISODE)
                .map(event -> new EventKey(
                        episodeToSeries.get(event.media().getId()), event.date()))
                .filter(key -> key.mediaId() != null)
                .collect(Collectors.toSet());

        List<ReportEvent> events = rawEvents.stream()
                .filter(event -> !(event.watch() && activityEpisodeKeys.contains(
                        new EventKey(event.media().getId(), event.date()))))
                .filter(event -> !(typeOf(event.media()) == MediaType.SERIES
                        && event.type() == ProfileActivityType.COMPLETED
                        && episodeSeriesKeys.contains(new EventKey(event.media().getId(), event.date()))))
                .map(event -> {
                    UUID canonicalId = canonicalId(event.media(), episodeToSeries, trackToAlbum);
                    return canonicalId == null ? null : new ReportEvent(
                            canonicalId,
                            typeOf(event.media()) == MediaType.EPISODE ? event.media().getId() : null,
                            event.date());
                })
                .filter(Objects::nonNull)
                .toList();

        Set<UUID> mediaIds = events.stream()
                .flatMap(event -> java.util.stream.Stream.of(event.mediaId(), event.sourceMediaId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Media> mediaById = new LinkedHashMap<>();
        List<UUID> reportMediaIds = List.copyOf(mediaIds);
        for (int start = 0; start < reportMediaIds.size(); start += 100) {
            mediaRepository.findAllWithGenresByIdIn(
                    reportMediaIds.subList(start, Math.min(start + 100, reportMediaIds.size())))
                    .forEach(media -> mediaById.put(media.getId(), media));
        }
        List<ReportEvent> hydratedEvents = events.stream()
                .map(event -> mediaById.containsKey(event.mediaId())
                        ? event.withMedia(mediaById.get(event.mediaId()), mediaById.get(event.sourceMediaId())) : null)
                .filter(Objects::nonNull)
                .toList();

        List<ReportEvent> selectedEvents = hydratedEvents.stream()
                .filter(event -> isReportType(typeOf(event.media())))
                .filter(event -> requestedType == null || typeOf(event.media()) == requestedType)
                .toList();
        List<ReportEvent> periodEvents = selectedEvents.stream()
                .filter(event -> !event.date().isBefore(selectedFrom) && !event.date().isAfter(selectedTo))
                .toList();
        List<ConsumptionReportPeriodOption> availablePeriods = availablePeriods(
                selectedEvents, period);

        return new ConsumptionReportResponse(
                period, year, month, requestedType, periodEvents.size(), availablePeriods,
                rankPeople(periodEvents, CreditRole.DIRECTOR,
                        Set.of(MediaType.MOVIE, MediaType.SERIES)),
                rankPeople(periodEvents, CreditRole.ARTIST,
                        Set.of(MediaType.ALBUM)),
                rankStrings(periodEvents, this::genresFor),
                rankStrings(periodEvents, this::countryFor),
                rankStrings(periodEvents, this::languageFor,
                        String::toUpperCase),
                rankSingleString(periodEvents, this::releaseYearFor)
        );
    }

    private void validate(ConsumptionReportPeriod period, int year, Integer month, MediaType type) {
        if (period == null || year < 1 || year > 9999) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_PERIOD",
                    "O período informado é inválido");
        }
        if (period == ConsumptionReportPeriod.MONTH && (month == null || month < 1 || month > 12)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_MONTH",
                    "O mês é obrigatório para relatórios mensais");
        }
        if (period == ConsumptionReportPeriod.YEAR && month != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_MONTH",
                    "O mês não deve ser informado para relatórios anuais");
        }
        if (type != null && !REPORT_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_MEDIA_TYPE",
                    "Este tipo de mídia não está disponível no relatório");
        }
    }

    private Map<UUID, UUID> loadEpisodeParents(Collection<RawEvent> events) {
        Set<UUID> ids = events.stream().filter(event -> typeOf(event.media()) == MediaType.EPISODE)
                .map(event -> event.media().getId()).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return seriesEpisodeRepository.findAllByEpisodeMediaIdIn(ids).stream()
                .collect(Collectors.toMap(
                        episode -> episode.getEpisodeMedia().getId(),
                        episode -> episode.getSeason().getSeries().getId(),
                        (first, ignored) -> first));
    }

    private Map<UUID, UUID> loadAlbumParents(Collection<RawEvent> events) {
        Set<UUID> ids = events.stream().filter(event -> typeOf(event.media()) == MediaType.TRACK)
                .map(event -> event.media().getId()).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return albumTrackRepository.findAllByTrackMediaIdIn(ids).stream()
                .sorted(Comparator.comparing(track -> track.getAlbum().getId().toString()))
                .collect(Collectors.toMap(
                        track -> track.getTrackMedia().getId(),
                        track -> track.getAlbum().getId(),
                        (first, ignored) -> first));
    }

    private UUID canonicalId(Media media, Map<UUID, UUID> episodeToSeries, Map<UUID, UUID> trackToAlbum) {
        MediaType type = typeOf(media);
        if (type == null) return null;
        return switch (type) {
            case EPISODE -> episodeToSeries.get(media.getId());
            case TRACK -> trackToAlbum.get(media.getId());
            default -> media.getId();
        };
    }

    private boolean isReportType(MediaType type) {
        return type != null && REPORT_TYPES.contains(type);
    }

    private MediaType typeOf(Media media) {
        return media == null ? null : media.getType();
    }

    private ConsumptionReportSection rankPeople(
            List<ReportEvent> events,
            CreditRole role,
            Set<MediaType> eligibleTypes
    ) {
        List<ReportEvent> eligible = events.stream()
                .filter(event -> eligibleTypes.contains(event.media().getType())).toList();
        if (eligible.isEmpty()) {
            return new ConsumptionReportSection(List.of(), 0, 0);
        }
        String eventsJson = IntStream.range(0, eligible.size())
                .mapToObj(index -> {
                    ReportEvent event = eligible.get(index);
                    return "{\"event_id\":" + index
                            + ",\"media_id\":\"" + event.media().getId() + "\""
                            + ",\"source_media_id\":" + (event.sourceMedia() == null
                            ? "null" : "\"" + event.sourceMedia().getId() + "\"")
                            + ",\"event_date\":\"" + event.date() + "\"}";
                })
                .collect(Collectors.joining(",", "[", "]"));
        List<ReportRankingProjection> ranking = mediaCreditRepository.rankReportPeople(
                eventsJson, role.name(), RANKING_LIMIT);
        if (ranking.isEmpty()) {
            return new ConsumptionReportSection(List.of(), eligible.size(), 0);
        }
        ReportRankingProjection totals = ranking.getFirst();
        List<ConsumptionReportItem> items = ranking.stream()
                .filter(row -> row.getPersonId() != null)
                .map(row -> new ConsumptionReportItem(row.getPersonId().toString(), row.getPersonName(),
                        row.getPersonId(), row.getPersonImageUrl(), row.getEventCount()))
                .toList();
        return new ConsumptionReportSection(items, totals.getEligibleEventCount(), totals.getAttributedEventCount());
    }

    private Collection<String> genresFor(ReportEvent event) {
        if (event.sourceMedia() != null && hasNonBlank(event.sourceMedia().getGenres())) {
            return event.sourceMedia().getGenres();
        }
        return event.media().getGenres() == null ? List.of() : event.media().getGenres();
    }

    private Collection<String> countryFor(ReportEvent event) {
        String value = firstNonBlank(
                event.sourceMedia() == null ? null : event.sourceMedia().getCountryCode(),
                event.media().getCountryCode());
        return nullableList(value);
    }

    private Collection<String> languageFor(ReportEvent event) {
        String value = firstNonBlank(
                event.sourceMedia() == null ? null : event.sourceMedia().getOriginalLanguage(),
                event.media().getOriginalLanguage());
        return nullableList(value);
    }

    private Collection<String> releaseYearFor(ReportEvent event) {
        LocalDate date = event.sourceMedia() != null && event.sourceMedia().getReleaseDate() != null
                ? event.sourceMedia().getReleaseDate() : event.media().getReleaseDate();
        return date == null ? List.of() : List.of(String.valueOf(date.getYear()));
    }

    private boolean hasNonBlank(Collection<String> values) {
        return values != null && values.stream().anyMatch(value -> value != null && !value.trim().isEmpty());
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.trim().isEmpty() ? preferred : fallback;
    }

    private ConsumptionReportSection rankStrings(
            List<ReportEvent> events,
            Function<ReportEvent, Collection<String>> values
    ) {
        return rankStrings(events, values, Function.identity());
    }

    private ConsumptionReportSection rankStrings(
            List<ReportEvent> events,
            Function<ReportEvent, Collection<String>> values,
            Function<String, String> labelTransform
    ) {
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        long attributed = 0;
        for (ReportEvent event : events) {
            Collection<String> eventValues = values.apply(event);
            List<String> normalized = (eventValues == null ? List.<String>of() : eventValues).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (!normalized.isEmpty()) attributed++;
            normalized.forEach(value -> {
                String key = value.toLowerCase(java.util.Locale.ROOT);
                add(aggregates, key, labelTransform.apply(value), null, null, event.date());
            });
        }
        return section(aggregates, events.size(), attributed, RANKING_LIMIT);
    }

    private ConsumptionReportSection rankSingleString(
            List<ReportEvent> events, Function<ReportEvent, Collection<String>> values
    ) {
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        long attributed = 0;
        for (ReportEvent event : events) {
            Collection<String> eventValues = values.apply(event);
            List<String> normalized = (eventValues == null ? List.<String>of() : eventValues).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (!normalized.isEmpty()) attributed++;
            normalized.forEach(value -> add(aggregates, value.toLowerCase(java.util.Locale.ROOT), value,
                    null, null, event.date()));
        }
        return section(aggregates, events.size(), attributed, 1);
    }

    private ConsumptionReportSection section(
            Map<String, Aggregate> aggregates, long eligible, long attributed, int limit
    ) {
        List<ConsumptionReportItem> items = aggregates.values().stream()
                .sorted(Comparator.comparingLong(Aggregate::eventCount).reversed()
                        .thenComparing(Aggregate::lastDate, Comparator.reverseOrder())
                        .thenComparing(Aggregate::key))
                .limit(limit)
                .map(value -> new ConsumptionReportItem(value.key(), value.label(),
                        value.personId(), value.imageUrl(), value.eventCount()))
                .toList();
        return new ConsumptionReportSection(items, eligible, attributed);
    }

    private void add(Map<String, Aggregate> aggregates, String key, String label,
                     UUID personId, String imageUrl, LocalDate date) {
        Aggregate value = aggregates.computeIfAbsent(key,
                ignored -> new Aggregate(key, label, personId, imageUrl));
        value.increment(date);
    }

    private List<ConsumptionReportPeriodOption> availablePeriods(
            List<ReportEvent> events, ConsumptionReportPeriod period
    ) {
        return events.stream().map(ReportEvent::date).map(date -> period == ConsumptionReportPeriod.YEAR
                        ? new ConsumptionReportPeriodOption(date.getYear(), null)
                        : new ConsumptionReportPeriodOption(date.getYear(), date.getMonthValue()))
                .distinct()
                .sorted(Comparator.comparingInt(ConsumptionReportPeriodOption::year).reversed()
                        .thenComparing(option -> option.month() == null ? 0 : option.month(), Comparator.reverseOrder()))
                .toList();
    }

    private static Collection<String> nullableList(String value) {
        return value == null ? List.of() : List.of(value);
    }

    private record RawEvent(Media media, LocalDate date, ProfileActivityType type, boolean watch) {
    }

    private record ReportEvent(
            UUID mediaId, UUID sourceMediaId, LocalDate date, Media media, Media sourceMedia
    ) {
        private ReportEvent(UUID mediaId, UUID sourceMediaId, LocalDate date) {
            this(mediaId, sourceMediaId, date, null, null);
        }

        private ReportEvent withMedia(Media value, Media source) {
            return new ReportEvent(mediaId, sourceMediaId, date, value, source);
        }
    }

    private record EventKey(UUID mediaId, LocalDate date) {
    }

    private static final class Aggregate {
        private final String key;
        private final String label;
        private final UUID personId;
        private final String imageUrl;
        private long eventCount;
        private LocalDate lastDate = LocalDate.MIN;

        private Aggregate(String key, String label, UUID personId, String imageUrl) {
            this.key = key;
            this.label = label;
            this.personId = personId;
            this.imageUrl = imageUrl;
        }

        private void increment(LocalDate date) {
            eventCount++;
            if (date.isAfter(lastDate)) lastDate = date;
        }

        private String key() { return key; }
        private String label() { return label; }
        private UUID personId() { return personId; }
        private String imageUrl() { return imageUrl; }
        private long eventCount() { return eventCount; }
        private LocalDate lastDate() { return lastDate; }
    }
}
