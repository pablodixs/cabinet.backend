package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaTranslation;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.TranslationStatus;
import com.scriptles.cabinet.media.repository.MediaTranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMediaTranslationResolverTest {
    @Mock
    private MediaTranslationRepository repository;

    private DefaultMediaTranslationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultMediaTranslationResolver(repository, new CatalogLocaleResolver());
    }

    @Test
    void resolvesExactPortugueseAndEnglishTranslations() {
        Media media = media("Canônico", "pt-BR");
        MediaTranslation pt = translation(media, "pt-BR", "Clube da Luta", "Descrição", null,
                TranslationStatus.AVAILABLE);
        MediaTranslation en = translation(media, "en-US", "Fight Club", "Description", null,
                TranslationStatus.AVAILABLE);
        pt.setCoverUrl("https://covers/fight-club-pt.jpg");
        en.setCoverUrl("https://covers/fight-club-en.jpg");
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of(en, pt));

        ResolvedMediaTranslation result = resolver.resolve(media, "pt-BR");

        assertThat(result.title()).isEqualTo("Clube da Luta");
        assertThat(result.coverUrl()).isEqualTo("https://covers/fight-club-pt.jpg");
        assertThat(result.resolvedLocale()).isEqualTo("pt-BR");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void fallsBackToSecondaryLocaleWhenRequestedTranslationIsMissing() {
        Media media = media("Canônico", "pt-BR");
        MediaTranslation en = translation(media, "en-US", "Fight Club", "Description", "Mischief",
                TranslationStatus.AVAILABLE);
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of(en));

        ResolvedMediaTranslation result = resolver.resolve(media, "pt-BR");

        assertThat(result.title()).isEqualTo("Fight Club");
        assertThat(result.resolvedLocale()).isEqualTo("en-US");
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void usesPartialRequestedTranslationBeforeSecondaryAvailableTranslation() {
        Media media = media("Canônico", "pt-BR");
        MediaTranslation pt = translation(media, "pt-BR", "Título PT", null, null,
                TranslationStatus.PARTIAL);
        MediaTranslation en = translation(media, "en-US", "English", "English description", null,
                TranslationStatus.AVAILABLE);
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of(en, pt));

        ResolvedMediaTranslation result = resolver.resolve(media, "pt-BR");

        assertThat(result.title()).isEqualTo("Título PT");
        assertThat(result.description()).isEqualTo("English description");
        assertThat(result.resolvedLocale()).isEqualTo("pt-BR");
        assertThat(result.partialFallback()).isTrue();
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void ignoresStaleAndMissingTranslationsWhenValidFallbackExists() {
        Media media = media("Canônico", "pt-BR");
        MediaTranslation stale = translation(media, "pt-BR", "Velho", "Velha", null,
                TranslationStatus.STALE);
        MediaTranslation available = translation(media, "en-US", "Fresh", "Fresh description", null,
                TranslationStatus.AVAILABLE);
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of(stale, available));

        assertThat(resolver.resolve(media, "pt-BR").title()).isEqualTo("Fresh");
    }

    @Test
    void fallsBackFieldByFieldAndFinallyToCanonicalMedia() {
        Media media = media("Título canônico", "pt-BR");
        media.setDescription("Descrição canônica");
        media.setTagline("Tagline canônica");
        MediaTranslation pt = translation(media, "pt-BR", "Título traduzido", null, null,
                TranslationStatus.PARTIAL);
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of(pt));

        ResolvedMediaTranslation result = resolver.resolve(media, "pt-BR");

        assertThat(result.title()).isEqualTo("Título traduzido");
        assertThat(result.description()).isEqualTo("Descrição canônica");
        assertThat(result.tagline()).isEqualTo("Tagline canônica");
        assertThat(result.resolvedLocale()).isEqualTo("pt-BR");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void reportsPartialFallbackWhenOnlyTheLocalizedCoverIsMissing() {
        Media media = media("Canonical title", "en-US");
        media.setCoverUrl("https://covers/canonical.jpg");
        MediaTranslation pt = translation(media, "pt-BR", "Título traduzido", null, null,
                TranslationStatus.PARTIAL);
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of(pt));

        ResolvedMediaTranslation result = resolver.resolve(media, "pt-BR");

        assertThat(result.title()).isEqualTo("Título traduzido");
        assertThat(result.coverUrl()).isEqualTo("https://covers/canonical.jpg");
        assertThat(result.partialFallback()).isTrue();
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void reportsCanonicalLocaleWhenNoTranslationExists() {
        Media media = media("Título canônico", "pt-BR");
        media.setCoverUrl("https://covers/canonical.jpg");
        when(repository.findAllByMediaId(media.getId())).thenReturn(List.of());

        ResolvedMediaTranslation result = resolver.resolve(media, "en-US");

        assertThat(result.coverUrl()).isEqualTo("https://covers/canonical.jpg");
        assertThat(result.resolvedLocale()).isEqualTo("pt-BR");
        assertThat(result.fallback()).isTrue();
        assertThat(result.status()).isEqualTo(TranslationStatus.FALLBACK);
    }

    @Test
    void batchResolutionUsesOneRepositoryQueryAndIsStableRegardlessOfDatabaseOrder() {
        Media first = media("Primeiro", "pt-BR");
        Media second = media("Segundo", "pt-BR");
        MediaTranslation firstEn = translation(first, "en-US", "First", null, null,
                TranslationStatus.PARTIAL);
        MediaTranslation firstPt = translation(first, "pt-BR", "Primeiro traduzido", null, null,
                TranslationStatus.PARTIAL);
        MediaTranslation secondEn = translation(second, "en-US", "Second", null, null,
                TranslationStatus.PARTIAL);
        when(repository.findForResolution(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(secondEn, firstEn, firstPt));

        Map<UUID, ResolvedMediaTranslation> result = resolver.resolveAll(List.of(first, second), "pt-BR");

        assertThat(result.get(first.getId()).title()).isEqualTo("Primeiro traduzido");
        assertThat(result.get(second.getId()).title()).isEqualTo("Second");
        verify(repository).findForResolution(List.of(first.getId(), second.getId()));
    }

    @Test
    void emptyBatchDoesNotQueryRepository() {
        assertThat(resolver.resolveAll(List.of(), "pt-BR")).isEmpty();
    }

    private Media media(String title, String defaultLocale) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setTitle(title);
        media.setOriginalTitle(title);
        media.setDefaultLocale(defaultLocale);
        return media;
    }

    private MediaTranslation translation(
            Media media,
            String locale,
            String title,
            String description,
            String tagline,
            TranslationStatus status
    ) {
        MediaTranslation translation = new MediaTranslation();
        translation.setId(UUID.randomUUID());
        translation.setMedia(media);
        translation.setLocale(locale);
        translation.setTitle(title);
        translation.setDescription(description);
        translation.setTagline(tagline);
        translation.setTranslationStatus(status);
        translation.setSource(ExternalSource.TMDB);
        return translation;
    }
}
