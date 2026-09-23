package com.scriptles.cabinet.catalog.service;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TmdbCatalogIndexServiceTest {

    @Test
    void importsGzippedExportUsingJdbcSupportedTimestampType() throws Exception {
        byte[] export = gzip("{\"id\":123,\"adult\":false,\"popularity\":4.5}\n");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collection_ids_09_23_2026.json.gz", exchange -> {
            exchange.sendResponseHeaders(200, export.length);
            exchange.getResponseBody().write(export);
            exchange.close();
        });
        server.start();

        try {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(invocation ->
                    new int[((List<?>) invocation.getArgument(1)).size()]);
            when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
            TmdbCatalogIndexService service = new TmdbCatalogIndexService(
                    jdbc, new ObjectMapper(), new SimpleMeterRegistry(), 2_000,
                    Duration.ofSeconds(2), Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort());

            UUID runId = UUID.randomUUID();
            TmdbCatalogIndexService.IndexResult result = service.importCollections("09_23_2026", runId);

            assertThat(result.discovered()).isEqualTo(1);
            @SuppressWarnings("unchecked")
            var batchCaptor = org.mockito.ArgumentCaptor.forClass((Class<List<Object[]>>) (Class<?>) List.class);
            verify(jdbc).batchUpdate(anyString(), batchCaptor.capture());
            Object[] arguments = batchCaptor.getValue().getFirst();
            assertThat(arguments[0]).isEqualTo("123");
            assertThat(arguments[1]).isEqualTo("{\"adult\":false,\"popularity\":4.5}");
            assertThat(arguments)
                    .filteredOn(value -> value instanceof java.time.temporal.Temporal)
                    .allMatch(OffsetDateTime.class::isInstance);
            assertThat(arguments[4]).isEqualTo(runId);
            verify(jdbc).update(anyString(), any(Object[].class));
        } finally {
            server.stop(0);
        }
    }

    private byte[] gzip(String body) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
}
