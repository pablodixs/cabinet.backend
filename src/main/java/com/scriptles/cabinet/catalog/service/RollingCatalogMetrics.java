package com.scriptles.cabinet.catalog.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class RollingCatalogMetrics {
    private final Instant startedAt = Instant.now();
    private final Deque<Sample> media = new ArrayDeque<>();
    private final Deque<Sample> credits = new ArrayDeque<>();
    private final Deque<Sample> people = new ArrayDeque<>();

    public synchronized void mediaMaterialized() { add(media, 1); }
    public synchronized void creditsPersisted(long count) { add(credits, count); }
    public synchronized void peopleResolved(long count) { add(people, count); }

    public synchronized long mediaLastHour() { return sum(media); }
    public synchronized long creditsLastHour() { return sum(credits); }
    public synchronized long peopleLastHour() { return sum(people); }
    public Instant startedAt() { return startedAt; }

    private void add(Deque<Sample> samples, long count) {
        if (count > 0) samples.addLast(new Sample(Instant.now(), count));
        prune(samples);
    }

    private long sum(Deque<Sample> samples) {
        prune(samples);
        return samples.stream().mapToLong(Sample::count).sum();
    }

    private void prune(Deque<Sample> samples) {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        while (!samples.isEmpty() && samples.peekFirst().at().isBefore(cutoff)) samples.removeFirst();
    }

    private record Sample(Instant at, long count) {}
}
