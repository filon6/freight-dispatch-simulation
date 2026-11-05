package org.example.smo.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomStreamsTest {
    @Test
    void sameSeedProducesSameSequence() {
        RandomProvider first = new RandomStreams(42).forSource(1);
        RandomProvider second = new RandomStreams(42).forSource(1);

        for (int i = 0; i < 1000; i++) {
            assertEquals(
                first.nextDouble(),
                second.nextDouble()
            );
        }
    }

    @Test
    void sourceIsIndependentOfProcessor() {
        RandomStreams streams = new RandomStreams(42);
        RandomProvider source = streams.forSource(1);
        RandomProvider copy = new RandomStreams(42).forSource(1);
        RandomProvider processor = streams.forProcessor(1);

        for (int i = 0; i < 100; i++) {
            processor.nextDouble();
            processor.nextDouble();

            assertEquals(
                copy.nextDouble(),
                source.nextDouble()
            );
        }
    }

    @Test
    void invalidStreamIdIsRejected() {
        RandomStreams streams = new RandomStreams(42);

        assertThrows(
            IllegalArgumentException.class,
            () -> streams.forSource(0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> streams.forProcessor(-1)
        );
    }
}
