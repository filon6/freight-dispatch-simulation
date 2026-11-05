package org.example.smo.source;

import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.model.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceArrivalPlannerTest {
    @Test
    void initialArrivalsArePlannedForAllSources() {
        Source first = new Source(1, "Portal", 1.0, () -> 0.5);
        Source second = new Source(2, "Mobile", 2.0, () -> 0.5);
        SourceArrivalPlanner planner = new SourceArrivalPlanner(List.of(first, second));
        Map<Integer, Double> arrivals = planner.planInitialArrivals(0.0);

        assertEquals(2, arrivals.size());

        assertEquals(
            -Math.log(0.5),
            arrivals.get(1),
            1e-12
        );

        assertEquals(
            -Math.log(0.5) / 2.0,
            arrivals.get(2),
            1e-12
        );
    }

    @Test
    void nextArrivalIsPlannedWhilePreviousIsProcessing() {
        Source source = new Source(1, "Portal", 1.0, () -> 0.5);
        SourceArrivalPlanner planner = new SourceArrivalPlanner(List.of(source));
        Map<Integer, Double> initial = planner.planInitialArrivals(0.0);

        double firstArrival = initial.get(1);

        Request request =
            source.generateRequest(1, firstArrival);

        request.markQueued();
        request.markProcessing();

        assertEquals(
            RequestStatus.PROCESSING,
            request.getStatus()
        );

        double nextArrival = planner.onArrival(1, firstArrival);

        assertTrue(nextArrival > firstArrival);

        assertEquals(
            1,
            planner.scheduledArrivals().size()
        );

        assertEquals(
            nextArrival,
            planner.scheduledArrivals().get(1),
            1e-12
        );

        assertEquals(
            RequestStatus.PROCESSING,
            request.getStatus()
        );
    }

    @Test
    void arrivalCannotBeProcessedTwice() {
        Source source = new Source(1, "Portal", 1.0, () -> 0.5);

        SourceArrivalPlanner planner = new SourceArrivalPlanner(List.of(source));
        double firstArrival = planner.planInitialArrivals(0.0).get(1);
        planner.onArrival(1, firstArrival);

        assertThrows(
            IllegalStateException.class,
            () -> planner.onArrival(1, firstArrival)
        );
    }
}
