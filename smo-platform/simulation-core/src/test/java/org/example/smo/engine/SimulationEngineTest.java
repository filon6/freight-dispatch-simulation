package org.example.smo.engine;

import org.example.smo.event.EventType;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.model.Source;
import org.example.smo.random.RandomStreams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {
    private static final Map<EventType, Integer> PRIORITIES = Map.of(
        EventType.SERVICE_COMPLETED, 0,
        EventType.ARRIVAL, 1
    );

    @Test
    void shouldScheduleOneArrivalPerSource() {
        Source first = new Source(1, "Portal", 1.0, () -> 0.5);
        Source second = new Source(2, "Mobile", 2.0, () -> 0.5);

        SimulationEngine engine = new SimulationEngine(
            List.of(first, second),
            10,
            PRIORITIES
        );

        engine.initialize();

        assertEquals(2, engine.getCalendar().size());

        engine.step();

        assertEquals(2, engine.getCalendar().size());

        for (int sourceId = 1; sourceId <= 2; sourceId++) {
            final int id = sourceId;

            long count = engine.getCalendar()
                .snapshotSorted()
                .stream()
                .filter(event -> event.sourceId() == id)
                .count();

            assertEquals(1, count);
        }
    }

    @Test
    void shouldNotWaitForPreviousRequestCompletion() {
        Source source = new Source(1, "Portal", 1.0, () -> 0.5);

        SimulationEngine engine = new SimulationEngine(
            List.of(source),
            2,
            PRIORITIES
        );

        engine.initialize();

        Request first = engine.step();
        Request second = engine.step();

        assertNotNull(first);
        assertNotNull(second);

        assertTrue(second.getCreatedAt() > first.getCreatedAt());

        assertEquals(RequestStatus.CREATED, first.getStatus());
        assertEquals(2, engine.getGeneratedTotal());

        assertTrue(engine.getCalendar().isEmpty());
    }

    @Test
    void shouldStopAfterTargetRequests() {
        Source first = new Source(1, "Portal", 1.0, () -> 0.5);
        Source second = new Source(2, "Mobile", 2.0, () -> 0.5);

        SimulationEngine engine = new SimulationEngine(
            List.of(first, second),
            3,
            PRIORITIES
        );

        engine.initialize();

        int created = 0;

        while (!engine.getCalendar().isEmpty()) {
            Request request = engine.step();

            if (request != null) {
                created++;
            }
        }

        assertEquals(3, created);
        assertEquals(3, engine.getGeneratedTotal());
    }

    @Test
    void shouldProduceSameArrivalsWithSameSeed() {
        List<Double> first = generateArrivalTimes(42);
        List<Double> second = generateArrivalTimes(42);

        assertEquals(first, second);
        assertEquals(10, first.size());
    }

    private List<Double> generateArrivalTimes(long seed) {
        RandomStreams streams = new RandomStreams(seed);

        Source first = new Source(
            1,
            "Portal",
            0.5,
            streams.forSource(1)
        );

        Source second = new Source(
            2,
            "Mobile",
            0.8,
            streams.forSource(2)
        );

        SimulationEngine engine = new SimulationEngine(
            List.of(first, second),
            10,
            PRIORITIES
        );

        engine.initialize();

        List<Double> arrivalTimes = new ArrayList<>();

        while (!engine.getCalendar().isEmpty()) {
            Request request = engine.step();

            if (request != null) {
                arrivalTimes.add(request.getCreatedAt());
            }
        }

        return arrivalTimes;
    }
}
