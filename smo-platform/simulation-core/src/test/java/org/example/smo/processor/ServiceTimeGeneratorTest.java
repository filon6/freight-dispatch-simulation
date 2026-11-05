package org.example.smo.processor;

import org.example.smo.random.RandomStreams;
import org.example.smo.random.SeededRandomProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTimeGeneratorTest {
    @Test
    void serviceTimeIsWithinRange() {
        ServiceTimeGenerator service =
            new ServiceTimeGenerator(
                2.0,
                5.0,
                new SeededRandomProvider(42)
            );

        for (int i = 0; i < 10000; i++) {
            double duration = service.sampleServiceTime();

            assertTrue(Double.isFinite(duration));
            assertTrue(duration >= 2.0);
            assertTrue(duration <= 5.0);
        }
    }

    @Test
    void sameSeedProducesSameServiceTimes() {
        RandomStreams streams = new RandomStreams(42);

        ServiceTimeGenerator first = new ServiceTimeGenerator(2.0, 5.0, streams.forProcessor(1));
        ServiceTimeGenerator second = new ServiceTimeGenerator(2.0, 5.0, streams.forProcessor(1));

        for (int i = 0; i < 1000; i++) {
            assertEquals(
                first.sampleServiceTime(),
                second.sampleServiceTime()
            );
        }
    }

    @Test
    void constantServiceTimeIsSupported() {
        ServiceTimeGenerator service = new ServiceTimeGenerator(3.0, 3.0, () -> 0.5);

        assertEquals(3.0, service.sampleServiceTime());
    }
}
