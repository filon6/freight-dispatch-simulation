package org.example.smo.event;

import org.example.smo.model.Clock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

public final class EventCalendar {
    private final Clock clock;
    private final Comparator<SimulationEvent> order;
    private final PriorityQueue<SimulationEvent> events;

    private long nextSequenceNo = 1;

    public EventCalendar(Clock clock, Map<EventType, Integer> priorities) {
        this.clock = Objects.requireNonNull(clock);
        Objects.requireNonNull(priorities);

        EnumMap<EventType, Integer> priorityCopy = new EnumMap<>(EventType.class);

        for (EventType type : EventType.values()) {
            Integer priority = priorities.get(type);

            if (priority == null) {
                throw new IllegalArgumentException(
                    "Missing event priority: " + type
                );
            }

            priorityCopy.put(type, priority);
        }

        if (new HashSet<>(priorityCopy.values()).size() != EventType.values().length) {
            throw new IllegalArgumentException("Event priorities must be distinct");
        }

        order = Comparator
            .comparingDouble(SimulationEvent::time)
            .thenComparingInt(event -> priorityCopy.get(event.type()))
            .thenComparingLong(SimulationEvent::sequenceNo);

        events = new PriorityQueue<>(order);
    }

    public SimulationEvent schedule(SimulationEvent event) {
        Objects.requireNonNull(event);

        if (event.time() < clock.getCurrentTime()) {
            throw new IllegalArgumentException("Cannot schedule an event in the past");
        }

        if (event.sequenceNo() != 0) {
            throw new IllegalArgumentException("Event already has a sequence number");
        }

        if (nextSequenceNo == Long.MAX_VALUE) {
            throw new IllegalStateException("Event sequence number overflow");
        }

        SimulationEvent scheduled = event.withSequenceNo(nextSequenceNo++);

        events.add(scheduled);

        return scheduled;
    }

    public SimulationEvent peek() {
        return events.peek();
    }

    public SimulationEvent popEarliest() {
        SimulationEvent next = events.peek();

        if (next == null) {
            return null;
        }

        clock.advanceTo(next.time());

        return events.poll();
    }

    public List<SimulationEvent> snapshotSorted() {
        List<SimulationEvent> result = new ArrayList<>(events);
        result.sort(order);

        return List.copyOf(result);
    }

    public int cancelPendingArrivals() {
        int before = events.size();

        events.removeIf(event -> event.type() == EventType.ARRIVAL);

        return before - events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public int size() {
        return events.size();
    }
}
