package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.common.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LetterboxdExportParserTest {
    private final LetterboxdExportParser parser = new LetterboxdExportParser();

    @Test
    void consolidatesCsvFilesAndPreservesQuotedMultilineHistory() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("watched.csv", "\ufeffDate,Name,Year,Letterboxd URI\r\n2024-01-01,Paris Texas,1984,https://boxd.it/paris\r\n");
        files.put("ratings.csv", "Date,Name,Year,Letterboxd URI,Rating\n2024-02-03,Paris Texas,1984,https://boxd.it/paris,4.5\n");
        files.put("diary.csv", "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n2024-02-03,Paris Texas,1984,https://boxd.it/paris,4.5,false,road trip,2024-02-02\n");
        files.put("reviews.csv", "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Review,Tags,Watched Date\n2024-02-03,Paris Texas,1984,https://boxd.it/paris,4.5,false,\"<p>Uma viagem, longa.</p>\n<p>Linda.</p>\",road trip,2024-02-02\n");
        files.put("lists/Favoritos.csv", "Position,Name,Year,Letterboxd URI,Notes\n1,Paris Texas,1984,https://boxd.it/paris,Meu favorito\n");

        var result = parser.parse(zip(files));

        assertThat(result).hasSize(1);
        var film = result.getFirst();
        assertThat(film.title()).isEqualTo("Paris Texas");
        assertThat(film.payload().watched()).isTrue();
        assertThat(film.payload().rating()).isEqualByComparingTo("4.5");
        assertThat(film.payload().activities()).hasSize(1);
        assertThat(film.payload().activities().getFirst().occurredOn()).isEqualTo(LocalDate.of(2024, 2, 2));
        assertThat(film.payload().activities().getFirst().review()).isEqualTo("Uma viagem, longa.\n\nLinda.");
        assertThat(film.payload().lists()).singleElement().satisfies(list -> {
            assertThat(list.name()).isEqualTo("Favoritos");
            assertThat(list.notes()).isEqualTo("Meu favorito");
        });
    }

    @Test
    void rejectsZipPathTraversal() throws Exception {
        assertThatThrownBy(() -> parser.parse(zip(Map.of("../watched.csv",
                "Date,Name,Year\n2024-01-01,Alien,1979\n"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("caminho inseguro");
    }

    @Test
    void requiresARecognizedLetterboxdCsv() throws Exception {
        assertThatThrownBy(() -> parser.parse(zip(Map.of("profile.csv", "Username\npablo\n"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("arquivos reconhecidos");
    }

    private MockMultipartFile zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "letterboxd.zip", "application/zip", output.toByteArray());
    }
}
