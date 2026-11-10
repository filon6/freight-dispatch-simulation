package org.example.smo.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorTest {
    private Processor createProcessor() {
        return new Processor(
            1,
            new ServiceTimeGenerator(2.0, 2.0, () -> 0.5)
        );
    }

    @Test
    void shouldStartAndCompleteService() {
        Processor processor = createProcessor();

        double completionTime = processor.start(10L, 5.0);

        assertEquals(7.0, completionTime);
        assertTrue(processor.isBusy());
        assertEquals(10L, processor.snapshot().currentRequestId());
        assertEquals(5.0, processor.snapshot().busySince());
        assertEquals(0.0, processor.snapshot().busyTime());

        processor.complete(10L, 7.0);

        assertFalse(processor.isBusy());
        assertNull(processor.snapshot().currentRequestId());
        assertEquals(2.0, processor.snapshot().busyTime());
    }

    @Test
    void shouldNotAssignSecondRequestToBusyProcessor() {
        Processor processor = createProcessor();

        processor.start(1L, 0.0);

        assertThrows(
            IllegalStateException.class,
            () -> processor.start(2L, 1.0)
        );

        assertEquals(1L, processor.snapshot().currentRequestId());
    }

    @Test
    void shouldRejectIncorrectCompletion() {
        Processor processor = createProcessor();

        processor.start(1L, 0.0);

        assertThrows(
            IllegalStateException.class,
            () -> processor.complete(2L, 2.0)
        );

        assertThrows(
            IllegalStateException.class,
            () -> processor.complete(1L, 1.0)
        );

        assertTrue(processor.isBusy());

        processor.complete(1L, 2.0);

        assertFalse(processor.isBusy());
    }

    @Test
    void shouldCountOnlyBusyTime() {
        Processor processor = createProcessor();

        processor.start(1L, 5.0);
        processor.complete(1L, 7.0);

        processor.start(2L, 20.0);
        processor.complete(2L, 22.0);

        assertEquals(4.0, processor.snapshot().busyTime());
    }

    @Test
    void shouldRejectInvalidProcessorId() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Processor(
                0,
                new ServiceTimeGenerator(2.0, 2.0, () -> 0.5)
            )
        );
    }
}
