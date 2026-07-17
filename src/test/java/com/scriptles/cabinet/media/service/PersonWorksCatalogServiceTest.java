package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.ExternalPersonWorksProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonWorksCatalogServiceTest {
    @Test
    void servesStaleCatalogWhenProviderFailsAfterFreshTtl() {
        ExternalPersonWorksProvider provider = mock(ExternalPersonWorksProvider.class);
        Clock clock = mock(Clock.class);
        Instant initial = Instant.parse("2026-07-16T12:00:00Z");
        when(clock.instant()).thenReturn(initial, initial.plusSeconds(7 * 60 * 60));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(provider.source()).thenReturn(ExternalSource.TMDB);
        ExternalMedia media = new ExternalMedia(
                ExternalSource.TMDB, "807", MediaType.MOVIE, "Se7en", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
        ExternalPersonWorksProvider.Work work = new ExternalPersonWorksProvider.Work(media, 10);
        when(provider.findPersonWorks("7467", "pt-BR"))
                .thenReturn(new ExternalPersonWorksProvider.PersonWorks(List.of(work), false))
                .thenThrow(new ExternalMediaException("provider unavailable"));
        PersonWorksCatalogService service = new PersonWorksCatalogService(List.of(provider), clock);

        assertThat(service.find(ExternalSource.TMDB, "7467", "pt-BR").incomplete()).isFalse();
        PersonWorksCatalogService.CatalogResult stale = service.find(
                ExternalSource.TMDB, "7467", "pt-BR");

        assertThat(stale.incomplete()).isTrue();
        assertThat(stale.items()).containsExactly(work);
        verify(provider, org.mockito.Mockito.times(2)).findPersonWorks("7467", "pt-BR");
    }
}
