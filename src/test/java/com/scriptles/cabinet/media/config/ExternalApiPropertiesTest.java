package com.scriptles.cabinet.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiPropertiesTest {

    @Test
    void bindsWikidataConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("external.wikidata.sparql-url", "https://wikidata.test/sparql")
                .withProperty("external.wikidata.user-agent", "cabinet-test/1.0")
                .withProperty("external.wikidata.read-timeout", "45s");

        ExternalApiProperties properties = Binder.get(environment)
                .bind("external", Bindable.of(ExternalApiProperties.class))
                .orElseThrow(() -> new AssertionError("External properties were not bound"));

        assertThat(properties.wikidata()).isNotNull();
        assertThat(properties.wikidata().sparqlUrl()).isEqualTo("https://wikidata.test/sparql");
        assertThat(properties.wikidata().userAgent()).isEqualTo("cabinet-test/1.0");
        assertThat(properties.wikidata().readTimeout()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void suppliesSafeWikidataDefaults() {
        ExternalApiProperties.Wikidata wikidata = new ExternalApiProperties.Wikidata(null, null, null);

        assertThat(wikidata.sparqlUrl()).isEqualTo("https://query.wikidata.org/sparql");
        assertThat(wikidata.userAgent()).startsWith("cabinet/1.0");
        assertThat(wikidata.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
