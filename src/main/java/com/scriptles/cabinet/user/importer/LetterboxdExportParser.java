package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class LetterboxdExportParser {
    static final long MAX_COMPRESSED_BYTES = 25L * 1024 * 1024;
    static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    static final long MAX_ENTRY_BYTES = 15L * 1024 * 1024;
    static final int MAX_ENTRIES = 1_000;
    static final int MAX_FILMS = 50_000;
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern BLOCK_TAG = Pattern.compile("(?i)</?(p|div|br|li|blockquote|h[1-6])[^>]*>");

    public List<ParsedFilm> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Selecione o ZIP exportado pelo Letterboxd");
        }
        if (file.getSize() > MAX_COMPRESSED_BYTES) {
            throw invalid("O arquivo excede o limite de 25 MB");
        }

        Map<String, FilmBuilder> films = new LinkedHashMap<>();
        boolean recognized = false;
        long totalBytes = 0;
        int entries = 0;

        try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw invalid("O ZIP contém arquivos demais");
                String path = safePath(entry.getName());
                if (entry.isDirectory() || path.startsWith("deleted/")) continue;
                byte[] bytes = readEntry(zip);
                totalBytes += bytes.length;
                if (totalBytes > MAX_UNCOMPRESSED_BYTES) throw invalid("O conteúdo do ZIP excede 100 MB");
                if (entry.getCompressedSize() > 0 && bytes.length / Math.max(1, entry.getCompressedSize()) > 150) {
                    throw invalid("O ZIP apresenta taxa de compressão insegura");
                }

                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.equals("watched.csv")) {
                    recognized = true;
                    consume(bytes, films, this::watched);
                } else if (lower.equals("watchlist.csv")) {
                    recognized = true;
                    consume(bytes, films, this::watchlist);
                } else if (lower.equals("ratings.csv")) {
                    recognized = true;
                    consume(bytes, films, this::rating);
                } else if (lower.equals("reviews.csv")) {
                    recognized = true;
                    consume(bytes, films, this::review);
                } else if (lower.equals("diary.csv")) {
                    recognized = true;
                    consume(bytes, films, this::diary);
                } else if (lower.equals("likes/films.csv") || lower.equals("likes.csv")) {
                    recognized = true;
                    consume(bytes, films, this::liked);
                } else if (lower.startsWith("lists/") && lower.endsWith(".csv")) {
                    recognized = true;
                    consumeList(bytes, films, path);
                }
                if (films.size() > MAX_FILMS) throw invalid("A exportação excede 50.000 filmes");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid("Não foi possível ler o ZIP do Letterboxd");
        }

        if (!recognized || films.isEmpty()) {
            throw invalid("O ZIP não contém arquivos reconhecidos do Letterboxd");
        }
        return films.values().stream().map(FilmBuilder::build).toList();
    }

    private void consume(byte[] bytes, Map<String, FilmBuilder> films,
                         BiConsumer<FilmBuilder, Map<String, String>> consumer) {
        List<Map<String, String>> rows = Csv.read(bytes);
        validateIdentityColumns(rows);
        for (Map<String, String> row : rows) consumer.accept(film(films, row), row);
    }

    private void consumeList(byte[] bytes, Map<String, FilmBuilder> films, String path) {
        List<Map<String, String>> rows = Csv.read(bytes);
        validateIdentityColumns(rows);
        String fileName = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
        String listName = fileName.isBlank() ? "Lista do Letterboxd" : fileName;
        String listKey = "list:" + normalize(path);
        int position = 0;
        for (Map<String, String> row : rows) {
            position++;
            FilmBuilder film = film(films, row);
            String name = value(row, "list name", "listname");
            film.lists.putIfAbsent(listKey, new LetterboxdItemPayload.ListMembership(
                    listKey,
                    hasText(name) ? name.trim() : listName,
                    position,
                    trimToNull(value(row, "notes", "note"))
            ));
        }
    }

    private void watched(FilmBuilder film, Map<String, String> row) {
        film.watched = true;
        film.watchedMarkedOn = later(film.watchedMarkedOn, date(row, "date"));
    }

    private void watchlist(FilmBuilder film, Map<String, String> row) {
        film.watchlist = true;
        film.watchlistAddedOn = later(film.watchlistAddedOn, date(row, "date"));
    }

    private void rating(FilmBuilder film, Map<String, String> row) {
        LocalDate on = date(row, "date");
        BigDecimal value = decimal(row, "rating");
        if (value != null && (film.ratingOn == null || on == null || !on.isBefore(film.ratingOn))) {
            film.rating = value;
            film.ratingOn = on;
        }
    }

    private void review(FilmBuilder film, Map<String, String> row) {
        LocalDate on = date(row, "date");
        String content = cleanReview(value(row, "review"));
        if (content != null && (film.reviewOn == null || on == null || !on.isBefore(film.reviewOn))) {
            film.review = content;
            film.reviewOn = on;
        }
        addActivity(film, row, content);
    }

    private void diary(FilmBuilder film, Map<String, String> row) {
        addActivity(film, row, cleanReview(value(row, "review")));
    }

    private void liked(FilmBuilder film, Map<String, String> row) {
        film.liked = true;
        film.likedOn = later(film.likedOn, date(row, "date"));
    }

    private void addActivity(FilmBuilder film, Map<String, String> row, String review) {
        LocalDate watchedOn = date(row, "watched date", "watcheddate");
        LocalDate loggedOn = date(row, "date");
        if (watchedOn == null && review == null) return;
        BigDecimal rating = decimal(row, "rating");
        boolean rewatch = bool(row, "rewatch");
        String key = "diary:" + film.sourceKey + ':' + watchedOn + ':' + loggedOn + ':' + rewatch;
        LetterboxdItemPayload.Activity existing = film.activities.get(key);
        film.activities.put(key, new LetterboxdItemPayload.Activity(
                key,
                watchedOn != null ? watchedOn : loggedOn,
                loggedOn,
                rewatch,
                rating != null ? rating : existing == null ? null : existing.rating(),
                review != null ? review : existing == null ? null : existing.review(),
                mergeTags(existing == null ? List.of() : existing.tags(), tags(value(row, "tags"))),
                false
        ));
        film.watched = true;
        if (rating != null && (film.ratingOn == null || loggedOn == null || !loggedOn.isBefore(film.ratingOn))) {
            film.rating = rating;
            film.ratingOn = loggedOn;
        }
    }

    private FilmBuilder film(Map<String, FilmBuilder> films, Map<String, String> row) {
        String uri = trimToNull(value(row, "letterboxd uri", "letterboxd url", "url", "uri"));
        String title = trimToNull(value(row, "name", "title"));
        Integer year = integer(value(row, "year"));
        if (title == null && uri == null) throw invalid("Há uma linha sem título nem URI do Letterboxd");
        String sourceKey = uri != null ? uri : "title:" + normalize(title) + ':' + year;
        return films.computeIfAbsent(sourceKey, ignored -> new FilmBuilder(sourceKey, uri,
                title != null ? title : uri, year));
    }

    private void validateIdentityColumns(List<Map<String, String>> rows) {
        if (rows.isEmpty()) return;
        Set<String> headers = rows.getFirst().keySet();
        if (headers.stream().noneMatch(header -> Set.of("name", "title", "letterboxd uri", "letterboxd url")
                .contains(header))) {
            throw invalid("Um CSV não contém colunas de identificação de filme");
        }
    }

    private byte[] readEntry(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (output.size() + read > MAX_ENTRY_BYTES) throw invalid("Um CSV excede 15 MB");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String safePath(String value) {
        String path = value == null ? "" : value.replace('\\', '/');
        if (path.startsWith("/") || path.contains("../") || path.equals("..") || path.indexOf('\0') >= 0) {
            throw invalid("O ZIP contém um caminho inseguro");
        }
        return path;
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{Alnum}]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    static String cleanReview(String value) {
        String text = trimToNull(value);
        if (text == null) return null;
        text = BLOCK_TAG.matcher(text).replaceAll("\n");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = HtmlUtils.htmlUnescape(text).replace("\r", "");
        return trimToNull(text.replaceAll("\n{3,}", "\n\n"));
    }

    private List<String> tags(String value) {
        if (!hasText(value)) return List.of();
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : value.split(",")) {
            String normalized = trimToNull(tag);
            if (normalized != null) tags.add(normalized.length() > 100 ? normalized.substring(0, 100) : normalized);
        }
        return List.copyOf(tags);
    }

    private List<String> mergeTags(List<String> first, List<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private LocalDate date(Map<String, String> row, String... names) {
        String value = value(row, names);
        if (!hasText(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw invalid("Data inválida no CSV: " + value);
        }
    }

    private BigDecimal decimal(Map<String, String> row, String name) {
        String value = value(row, name);
        if (!hasText(value)) return null;
        try {
            BigDecimal decimal = new BigDecimal(value.trim());
            return decimal.compareTo(new BigDecimal("0.5")) >= 0 && decimal.compareTo(new BigDecimal("5.0")) <= 0
                    ? decimal : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean bool(Map<String, String> row, String name) {
        String value = value(row, name);
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String value(Map<String, String> row, String... names) {
        for (String name : names) {
            String value = row.get(name);
            if (value != null) return value;
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Integer integer(String value) {
        try {
            return hasText(value) ? Integer.valueOf(value.trim()) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate later(LocalDate current, LocalDate candidate) {
        if (current == null) return candidate;
        if (candidate == null) return current;
        return candidate.isAfter(current) ? candidate : current;
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LETTERBOXD_EXPORT", message);
    }

    public record ParsedFilm(String sourceKey, String letterboxdUri, String title, Integer year,
                             LetterboxdItemPayload payload) {
    }

    private static final class FilmBuilder {
        private final String sourceKey;
        private final String uri;
        private final String title;
        private final Integer year;
        private boolean watched;
        private LocalDate watchedMarkedOn;
        private boolean watchlist;
        private LocalDate watchlistAddedOn;
        private BigDecimal rating;
        private LocalDate ratingOn;
        private String review;
        private LocalDate reviewOn;
        private boolean liked;
        private LocalDate likedOn;
        private final Map<String, LetterboxdItemPayload.Activity> activities = new LinkedHashMap<>();
        private final Map<String, LetterboxdItemPayload.ListMembership> lists = new LinkedHashMap<>();

        private FilmBuilder(String sourceKey, String uri, String title, Integer year) {
            this.sourceKey = sourceKey;
            this.uri = uri;
            this.title = title;
            this.year = year;
        }

        private ParsedFilm build() {
            if (watched && activities.isEmpty()) {
                String key = "watched:" + sourceKey + ':' + watchedMarkedOn;
                activities.put(key, new LetterboxdItemPayload.Activity(key, watchedMarkedOn, watchedMarkedOn,
                        false, rating, null, List.of(), true));
            }
            return new ParsedFilm(sourceKey, uri, title, year, new LetterboxdItemPayload(
                    watched, watchedMarkedOn, watchlist, watchlistAddedOn, rating, ratingOn,
                    review, reviewOn, liked, likedOn, List.copyOf(activities.values()), List.copyOf(lists.values())
            ));
        }
    }

    static final class Csv {
        private Csv() {
        }

        static List<Map<String, String>> read(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\ufeff') text = text.substring(1);
            List<List<String>> records = records(text);
            if (records.isEmpty()) return List.of();
            List<String> headers = records.getFirst().stream()
                    .map(header -> header.trim().toLowerCase(Locale.ROOT))
                    .toList();
            List<Map<String, String>> result = new ArrayList<>();
            for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
                List<String> record = records.get(rowIndex);
                if (record.stream().allMatch(String::isBlank)) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index++) {
                    row.put(headers.get(index), index < record.size() ? record.get(index) : "");
                }
                result.add(row);
            }
            return result;
        }

        private static List<List<String>> records(String input) {
            List<List<String>> records = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < input.length(); index++) {
                char character = input.charAt(index);
                if (quoted) {
                    if (character == '"') {
                        if (index + 1 < input.length() && input.charAt(index + 1) == '"') {
                            field.append('"');
                            index++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        field.append(character);
                    }
                } else if (character == '"' && field.isEmpty()) {
                    quoted = true;
                } else if (character == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (character == '\n' || character == '\r') {
                    if (character == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') index++;
                    row.add(field.toString());
                    field.setLength(0);
                    records.add(row);
                    row = new ArrayList<>();
                } else {
                    field.append(character);
                }
            }
            if (!field.isEmpty() || !row.isEmpty()) {
                row.add(field.toString());
                records.add(row);
            }
            if (quoted) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_LETTERBOXD_EXPORT", "CSV com campo entre aspas não finalizado");
            return records;
        }
    }
}
