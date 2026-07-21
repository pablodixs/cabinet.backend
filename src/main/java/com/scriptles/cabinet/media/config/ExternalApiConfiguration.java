package com.scriptles.cabinet.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({ExternalApiProperties.class, OmdbProperties.class})
public class ExternalApiConfiguration {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return restClientBuilder(READ_TIMEOUT);
    }

    @Bean(name = "wikidataRestClientBuilder")
    public RestClient.Builder wikidataRestClientBuilder(ExternalApiProperties properties) {
        ExternalApiProperties.Wikidata wikidata = properties.wikidata() == null
                ? ExternalApiProperties.Wikidata.defaults()
                : properties.wikidata();
        return restClientBuilder(wikidata.readTimeout());
    }

    private RestClient.Builder restClientBuilder(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
