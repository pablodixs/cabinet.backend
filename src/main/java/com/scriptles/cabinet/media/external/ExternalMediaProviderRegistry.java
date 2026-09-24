package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class ExternalMediaProviderRegistry {
    private final List<ExternalMediaProvider> providers;
    private final MeterRegistry meters;

    public ExternalMediaProviderRegistry(List<ExternalMediaProvider> providers, MeterRegistry meters) {
        this.providers = providers;
        this.meters = meters;
    }

    public ExternalMediaProvider get(ExternalSource source, MediaType mediaType) {
        ExternalMediaProvider provider = providers.stream()
                .filter(candidate -> candidate.source() == source && candidate.supports(mediaType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source %s does not support media type %s".formatted(source, mediaType)
                ));
        return new MeteredProvider(provider, meters);
    }

    private static final class MeteredProvider implements ExternalMediaProvider {
        private final ExternalMediaProvider delegate;
        private final MeterRegistry meters;

        private MeteredProvider(ExternalMediaProvider delegate, MeterRegistry meters) {
            this.delegate = delegate;
            this.meters = meters;
        }

        @Override public ExternalSource source() { return delegate.source(); }
        @Override public boolean supports(MediaType mediaType) { return delegate.supports(mediaType); }

        @Override public List<ExternalMedia> search(MediaType type, String query) {
            return call("search", () -> delegate.search(type, query));
        }

        @Override public List<ExternalMedia> search(MediaType type, String query, String language) {
            return call("search", () -> delegate.search(type, query, language));
        }

        @Override public List<ExternalMedia> search(MediaType type, String query, String language, int offset, int limit) {
            return call("search", () -> delegate.search(type, query, language, offset, limit));
        }

        @Override public List<ExternalMedia> searchAll(String query, String language) {
            return call("search_all", () -> delegate.searchAll(query, language));
        }

        @Override public List<ExternalMedia> searchAll(String query, String language, int offset, int limit) {
            return call("search_all", () -> delegate.searchAll(query, language, offset, limit));
        }

        @Override public Optional<ExternalMedia> findById(MediaType type, String externalId) {
            return call("lookup", () -> delegate.findById(type, externalId));
        }

        @Override public Optional<ExternalMedia> findById(MediaType type, String externalId, String language) {
            return call("lookup", () -> delegate.findById(type, externalId, language));
        }

        @Override public Optional<ExternalMedia> findCoreById(MediaType type, String externalId, String language) {
            return call("core_lookup", () -> delegate.findCoreById(type, externalId, language));
        }

        @Override public Optional<ExternalMedia> findEnrichmentById(MediaType type, String externalId, String language) {
            return call("enrichment_lookup", () -> delegate.findEnrichmentById(type, externalId, language));
        }

        private <T> T call(String operation, Supplier<T> action) {
            Timer.Sample sample = Timer.start(meters);
            meters.counter("cabinet.provider.requests", "provider", source().name(), "operation", operation).increment();
            String outcome = "success";
            try {
                return action.get();
            } catch (RuntimeException failure) {
                outcome = "failure";
                meters.counter("cabinet.provider.failures", "provider", source().name(), "operation", operation)
                        .increment();
                if (failure instanceof ExternalMediaRateLimitException) {
                    meters.counter("cabinet.provider.rate_limited", "provider", source().name(),
                            "operation", operation).increment();
                }
                throw failure;
            } finally {
                sample.stop(meters.timer("cabinet.provider.duration", "provider", source().name(),
                        "operation", operation, "outcome", outcome));
            }
        }
    }
}
