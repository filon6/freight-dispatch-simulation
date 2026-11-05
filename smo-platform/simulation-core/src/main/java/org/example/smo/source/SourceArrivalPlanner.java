package org.example.smo.source;

import org.example.smo.model.Source;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SourceArrivalPlanner {
    private final Map<Integer, Source> sources = new LinkedHashMap<>();
    private final Map<Integer, Double> arrivals = new LinkedHashMap<>();

    public SourceArrivalPlanner(Collection<Source> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Sources required");
        }

        for (Source source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("Null source");
            }

            if (this.sources.putIfAbsent(source.getId(), source) != null) {
                throw new IllegalArgumentException("Duplicate source");
            }
        }
    }

    public Map<Integer, Double> planInitialArrivals(double startTime) {
        validateTime(startTime);

        if (!arrivals.isEmpty()) {
            throw new IllegalStateException("Already initialized");
        }

        for (Source source : sources.values()) {
            arrivals.put(
                source.getId(),
                source.nextArrivalTime(startTime)
            );
        }

        return scheduledArrivals();
    }

    public double onArrival(int sourceId, double eventTime) {
        validateTime(eventTime);

        Double expected = arrivals.get(sourceId);

        if (expected == null) {
            throw new IllegalArgumentException("Unknown source");
        }

        if (Double.compare(expected, eventTime) != 0) {
            throw new IllegalStateException("Unexpected arrival time");
        }

        double next = sources
            .get(sourceId)
            .nextArrivalTime(eventTime);

        arrivals.put(sourceId, next);

        return next;
    }

    public Map<Integer, Double> scheduledArrivals() {
        return Map.copyOf(arrivals);
    }

    private static void validateTime(double time) {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Invalid time");
        }
    }
}
