package org.example.smo.engine;

import org.example.smo.event.EventCalendar;
import org.example.smo.event.EventType;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Clock;
import org.example.smo.model.Request;
import org.example.smo.model.Source;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class SimulationEngine {
    private final Clock clock = new Clock();
    private final EventCalendar calendar;
    private final Map<Integer, Source> sources = new TreeMap<>();
    private final long targetRequests;

    private long generatedTotal;
    private boolean initialized;

    public SimulationEngine(
        List<Source> sources,
        long targetRequests,
        Map<EventType, Integer> priorities
    ) {
        Objects.requireNonNull(sources);

        if (sources.isEmpty() || targetRequests <= 0) {
            throw new IllegalArgumentException("Invalid simulation parameters");
        }

        for (Source source : sources) {
            Objects.requireNonNull(source);

            if (this.sources.putIfAbsent(source.getId(), source) != null) {
                throw new IllegalArgumentException("Duplicate source ID");
            }
        }

        this.targetRequests = targetRequests;
        this.calendar = new EventCalendar(clock, priorities);
    }

    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Simulation already initialized");
        }

        initialized = true;

        for (Source source : sources.values()) {
            scheduleNextArrival(source);
        }
    }

    public Request step() {
        if (!initialized) {
            throw new IllegalStateException("Simulation is not initialized");
        }

        SimulationEvent upcoming = calendar.peek();

        if (upcoming == null) {
            return null;
        }

        if (upcoming.type() != EventType.ARRIVAL) {
            throw new UnsupportedOperationException("SERVICE_COMPLETED will be handled after processors are implemented");
        }

        SimulationEvent event = calendar.popEarliest();

        if (generatedTotal >= targetRequests) {
            return null;
        }

        Source source = sources.get(event.sourceId());

        Request request = source.generateRequest(
            generatedTotal + 1,
            clock.getCurrentTime()
        );

        generatedTotal++;

        if (generatedTotal < targetRequests) {
            scheduleNextArrival(source);
        }

        return request;
    }

    private void scheduleNextArrival(Source source) {
        double time = source.nextArrivalTime(clock.getCurrentTime());

        calendar.schedule(
            SimulationEvent.arrival(time, source.getId())
        );
    }

    public Clock getClock() {
        return clock;
    }

    public EventCalendar getCalendar() {
        return calendar;
    }

    public long getGeneratedTotal() {
        return generatedTotal;
    }
}
