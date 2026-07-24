package com.scriptles.cabinet.media.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedLocaleTest {
    @Test
    void normalizesSupportedLocalesAndDefaultsToPortuguese() {
        assertThat(SupportedLocale.from(null)).isEqualTo(SupportedLocale.PT_BR);
        assertThat(SupportedLocale.from("pt-br")).isEqualTo(SupportedLocale.PT_BR);
        assertThat(SupportedLocale.from("en-us")).isEqualTo(SupportedLocale.EN_US);
    }

    @Test
    void rejectsArbitraryLocales() {
        assertThatThrownBy(() -> SupportedLocale.from("es-ES"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pt-BR ou en-US");
    }
}
