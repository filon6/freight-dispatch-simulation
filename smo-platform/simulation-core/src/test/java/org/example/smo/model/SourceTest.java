package org.example.smo.model;

import org.example.smo.random.SeededRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceTest {

    @Test
    void sourceGeneratesRequest() {
        Source source = new Source(
            1,
            "Corporate Portal",
            0.5,
            new SeededRandomProvider(42)
        );

        Request request = source.generateRequest(10, 5.0);

        assertEquals(10, request.getId());
        assertEquals(1, request.getSourceId());
        assertEquals(5.0, request.getCreatedAt());
        Assertions.assertEquals(RequestStatus.CREATED, request.getStatus());
    }

    @Test
    void arrivalDelayIsNonNegativeAndFinite() {
        Source source = new Source(
            1,
            "Portal",
            0.5,
            new SeededRandomProvider(42)
        );

        for (int i = 0; i < 1000; i++) {
            double delay = source.sampleNextArrivalDelay();

            assertTrue(Double.isFinite(delay));
            assertTrue(delay >= 0);
        }
    }

    @Test
    void sameSeedProducesSameArrivalTimes() {
        Source first = new Source(1, "Portal", 0.5, new SeededRandomProvider(42));
        Source second = new Source(1, "Portal", 0.5, new SeededRandomProvider(42));

        for (int i = 0; i < 100; i++) {
            assertEquals(
                first.sampleNextArrivalDelay(),
                second.sampleNextArrivalDelay()
            );
        }
    }
}
