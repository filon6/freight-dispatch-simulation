package org.example.smo.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEventTest {

    @Test
    void shouldCreateArrivalEvent() {
        SimulationEvent event = SimulationEvent.arrival(2.5, 1);

        assertEquals(2.5, event.time());
        assertEquals(EventType.ARRIVAL, event.type());
        assertEquals(1, event.sourceId());
        assertNull(event.requestId());
        assertNull(event.processorId());
        assertEquals(0, event.sequenceNo());
    }

    @Test
    void shouldCreateServiceCompletedEvent() {
        SimulationEvent event = SimulationEvent.serviceCompleted(
            5.0,
            10L,
            1,
            2
        );

        assertEquals(5.0, event.time());
        assertEquals(EventType.SERVICE_COMPLETED, event.type());
        assertEquals(10L, event.requestId());
        assertEquals(1, event.sourceId());
        assertEquals(2, event.processorId());
    }

    @Test
    void shouldRejectInvalidEventTime() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEvent.arrival(-1.0, 1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEvent.arrival(Double.NaN, 1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEvent.arrival(Double.POSITIVE_INFINITY, 1)
        );
    }

    @Test
    void shouldRejectInvalidIdentifiers() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEvent.arrival(1.0, 0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEvent.serviceCompleted(2.0, -1L, 1, 1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEvent.serviceCompleted(2.0, 1L, 1, 0)
        );
    }

    @Test
    void shouldRejectInvalidSequenceNumber() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new SimulationEvent(
                1.0,
                EventType.ARRIVAL,
                null,
                1,
                null,
                -1
            )
        );
    }
}
