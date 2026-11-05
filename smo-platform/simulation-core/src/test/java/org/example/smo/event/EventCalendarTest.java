package org.example.smo.event;

import org.example.smo.model.Clock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventCalendarTest {

    private EventCalendar createCalendar(Clock clock) {
        return new EventCalendar(
            clock,
            Map.of(
                EventType.SERVICE_COMPLETED, 0,
                EventType.ARRIVAL, 1
            )
        );
    }

    @Test
    void shouldExtractEventsChronologically() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        calendar.schedule(SimulationEvent.arrival(5.0, 1));
        calendar.schedule(SimulationEvent.arrival(1.0, 2));
        calendar.schedule(SimulationEvent.arrival(3.0, 3));

        assertEquals(1.0, calendar.popEarliest().time());
        assertEquals(3.0, calendar.popEarliest().time());
        assertEquals(5.0, calendar.popEarliest().time());

        assertTrue(calendar.isEmpty());
        assertEquals(5.0, clock.getCurrentTime());
    }

    @Test
    void shouldUseEventTypePriorityForEqualTimes() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        calendar.schedule(SimulationEvent.arrival(5.0, 1));

        calendar.schedule(SimulationEvent.serviceCompleted(
            5.0,
            10L,
            1,
            1
        ));

        assertEquals(
            EventType.SERVICE_COMPLETED,
            calendar.popEarliest().type()
        );

        assertEquals(
            EventType.ARRIVAL,
            calendar.popEarliest().type()
        );
    }

    @Test
    void shouldUseSequenceNumberForEqualTypesAndTimes() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        SimulationEvent first = calendar.schedule(
            SimulationEvent.arrival(5.0, 1)
        );

        SimulationEvent second = calendar.schedule(
            SimulationEvent.arrival(5.0, 2)
        );

        assertTrue(first.sequenceNo() < second.sequenceNo());

        assertEquals(first, calendar.popEarliest());
        assertEquals(second, calendar.popEarliest());
    }

    @Test
    void shouldPeekWithoutRemovingEvent() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        calendar.schedule(SimulationEvent.arrival(2.0, 1));

        SimulationEvent event = calendar.peek();

        assertEquals(2.0, event.time());
        assertEquals(1, calendar.size());
        assertEquals(0.0, clock.getCurrentTime());
    }

    @Test
    void shouldReturnSortedSnapshotWithoutChangingCalendar() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        calendar.schedule(SimulationEvent.arrival(5.0, 1));
        calendar.schedule(SimulationEvent.arrival(1.0, 2));
        calendar.schedule(SimulationEvent.arrival(3.0, 3));

        List<SimulationEvent> snapshot = calendar.snapshotSorted();

        assertEquals(3, snapshot.size());
        assertEquals(1.0, snapshot.get(0).time());
        assertEquals(3.0, snapshot.get(1).time());
        assertEquals(5.0, snapshot.get(2).time());

        assertEquals(3, calendar.size());
        assertEquals(0.0, clock.getCurrentTime());

        assertThrows(
            UnsupportedOperationException.class,
            snapshot::clear
        );
    }

    @Test
    void shouldHandleEmptyCalendar() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        assertTrue(calendar.isEmpty());
        assertEquals(0, calendar.size());
        assertNull(calendar.peek());
        assertNull(calendar.popEarliest());
        assertTrue(calendar.snapshotSorted().isEmpty());
    }

    @Test
    void shouldRejectSchedulingInThePast() {
        Clock clock = new Clock(5.0);
        EventCalendar calendar = createCalendar(clock);

        assertThrows(
            IllegalArgumentException.class,
            () -> calendar.schedule(
                SimulationEvent.arrival(3.0, 1)
            )
        );

        assertTrue(calendar.isEmpty());
    }

    @Test
    void shouldAdvanceClockWhenPoppingEvent() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        calendar.schedule(SimulationEvent.arrival(2.0, 1));
        calendar.schedule(SimulationEvent.arrival(7.0, 2));

        calendar.popEarliest();

        assertEquals(2.0, clock.getCurrentTime());

        calendar.popEarliest();

        assertEquals(7.0, clock.getCurrentTime());
    }

    @Test
    void shouldRejectAlreadyScheduledEvent() {
        Clock clock = new Clock();
        EventCalendar calendar = createCalendar(clock);

        SimulationEvent event = calendar.schedule(
            SimulationEvent.arrival(3.0, 1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> calendar.schedule(event)
        );

        assertEquals(1, calendar.size());
    }
}
