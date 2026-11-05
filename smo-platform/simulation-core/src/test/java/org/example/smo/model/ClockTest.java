package org.example.smo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClockTest {
    @Test
    void shouldStartAtZero() {
        Clock clock = new Clock();

        assertEquals(0.0, clock.getCurrentTime());
    }

    @Test
    void shouldAllowCustomInitialTime() {
        Clock clock = new Clock(5.0);

        assertEquals(5.0, clock.getCurrentTime());
    }

    @Test
    void shouldAdvanceTimeForward() {
        Clock clock = new Clock();

        clock.advanceTo(2.5);
        clock.advanceTo(7.0);

        assertEquals(7.0, clock.getCurrentTime());
    }

    @Test
    void shouldAllowSameTime() {
        Clock clock = new Clock(5.0);

        clock.advanceTo(5.0);

        assertEquals(5.0, clock.getCurrentTime());
    }

    @Test
    void shouldRejectMovingBackwards() {
        Clock clock = new Clock(5.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> clock.advanceTo(3.0)
        );

        assertEquals(5.0, clock.getCurrentTime());
    }

    @Test
    void shouldRejectInvalidTime() {
        assertThrows(IllegalArgumentException.class, () -> new Clock(-1.0));
        assertThrows(IllegalArgumentException.class, () -> new Clock(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Clock(Double.POSITIVE_INFINITY));

        Clock clock = new Clock();

        assertThrows(IllegalArgumentException.class, () -> clock.advanceTo(-1.0));
        assertThrows(IllegalArgumentException.class, () -> clock.advanceTo(Double.NaN));
    }
}
