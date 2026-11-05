package org.example.smo.model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SourceTest {
    @Test
    void shouldGenerateRequest() {
        Source source = new Source(1, "Corporate Portal", 0.5, new Random(42));

        Request request = source.generateRequest(10L, 5.0);

        assertEquals(10L, request.getId());
        assertEquals(1, request.getSourceId());
        assertEquals(5.0, request.getCreatedAt());
        assertEquals(RequestStatus.CREATED, request.getStatus());
    }

    @Test
    void shouldGeneratePositiveArrivalDelay() {
        Source source = new Source(1, "Corporate Portal", 0.5, new Random(42));

        double delay = source.sampleNextArrivalDelay();

        assertTrue(Double.isFinite(delay));
        assertTrue(delay >= 0);
    }

    @Test
    void shouldGenerateReproducibleDelays() {
        Source first = new Source(1, "Portal", 0.5, new Random(42));
        Source second = new Source(1, "Portal", 0.5, new Random(42));

        for (int i = 0; i < 100; i++) {
            assertEquals(first.sampleNextArrivalDelay(), second.sampleNextArrivalDelay());
        }
    }

    @Test
    void shouldRejectInvalidLambda() {
        assertThrows(IllegalArgumentException.class,
            () -> new Source(1, "Portal", -0.5, new Random(42)));
    }

    @Test
    void shouldRejectInvalidSourceId() {
        assertThrows(IllegalArgumentException.class,
            () -> new Source(0, "Portal", 0.5, new Random(42)));
    }
}
