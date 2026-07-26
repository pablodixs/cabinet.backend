package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogLocaleResolverTest {
    private final CatalogLocaleResolver resolver = new CatalogLocaleResolver();

    @Test
    void explicitLocaleOverridesAcceptLanguage() {
        assertThat(resolver.resolve("pt-br", "en-US").tag()).isEqualTo("pt-BR");
    }

    @Test
    void selectsSupportedAcceptLanguageByQualityAndUnderstandsBaseLanguage() {
        assertThat(resolver.resolve(null, "es-ES;q=0.9, en;q=0.8, pt-BR;q=0.5"))
                .isEqualTo(SupportedLocale.EN_US);
    }

    @Test
    void defaultsForMissingMalformedOrUnsupportedHeader() {
        assertThat(resolver.resolve(null, null)).isEqualTo(SupportedLocale.PT_BR);
        assertThat(resolver.resolve(null, "not a valid header;")).isEqualTo(SupportedLocale.PT_BR);
        assertThat(resolver.resolve(null, "es-ES")).isEqualTo(SupportedLocale.PT_BR);
    }

    @Test
    void rejectsUnsupportedExplicitLocaleWithStableApiCode() {
        assertThatThrownBy(() -> resolver.resolve("es-ES", "pt-BR"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("UNSUPPORTED_LOCALE");
                    assertThat(error.getStatus().value()).isEqualTo(400);
                });
    }
}
