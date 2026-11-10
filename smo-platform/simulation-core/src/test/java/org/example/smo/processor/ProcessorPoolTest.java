package org.example.smo.processor;

import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.random.RandomStreams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorPoolTest {
    private ProcessorPool createPool(int count) {
        List<Processor> processors = IntStream.rangeClosed(1, count)
            .mapToObj(id -> new Processor(
                id,
                new ServiceTimeGenerator(2.0, 2.0, () -> 0.5)
            ))
            .toList();

        return new ProcessorPool(processors);
    }

    private Request createRequest(long id, double time) {
        Request request = new Request(
            id,
            1,
            time,
            "Cargo",
            "Origin",
            "Destination"
        );

        request.markQueued();

        return request;
    }

    @Test
    void shouldSelectProcessorsInCircularOrder() {
        ProcessorPool pool = createPool(3);

        SimulationEvent first = pool.assign(createRequest(1, 0.0), 0.0);
        SimulationEvent second = pool.assign(createRequest(2, 0.0), 0.0);
        SimulationEvent third = pool.assign(createRequest(3, 0.0), 0.0);

        assertEquals(1, first.processorId());
        assertEquals(2, second.processorId());
        assertEquals(3, third.processorId());

        assertEquals(1, pool.snapshot().nextProcessorId());

        pool.complete(first);

        SimulationEvent fourth = pool.assign(createRequest(4, 2.0), 2.0);

        assertEquals(1, fourth.processorId());
        assertEquals(2, pool.snapshot().nextProcessorId());
    }

    @Test
    void shouldSkipBusyProcessors() {
        ProcessorPool pool = createPool(3);

        pool.assign(createRequest(1, 0.0), 0.0);
        SimulationEvent second = pool.assign(createRequest(2, 0.0), 0.0);
        pool.assign(createRequest(3, 0.0), 0.0);

        pool.complete(second);

        SimulationEvent fourth = pool.assign(createRequest(4, 2.0), 2.0);

        assertEquals(2, fourth.processorId());
        assertEquals(3, pool.snapshot().nextProcessorId());
    }

    @Test
    void shouldReturnNullWhenAllProcessorsAreBusy() {
        ProcessorPool pool = createPool(2);

        pool.assign(createRequest(1, 0.0), 0.0);
        pool.assign(createRequest(2, 0.0), 0.0);

        Request waiting = createRequest(3, 0.0);

        assertFalse(pool.hasFreeProcessor());
        assertNull(pool.assign(waiting, 0.0));

        assertEquals(RequestStatus.QUEUED, waiting.getStatus());
        assertEquals(1, pool.snapshot().nextProcessorId());
    }

    @Test
    void shouldReleaseOnlyMatchingProcessor() {
        ProcessorPool pool = createPool(1);

        SimulationEvent event = pool.assign(createRequest(1, 0.0), 0.0);

        SimulationEvent incorrect = SimulationEvent.serviceCompleted(
            2.0,
            999L,
            1,
            1
        );

        assertThrows(
            IllegalStateException.class,
            () -> pool.complete(incorrect)
        );

        assertFalse(pool.hasFreeProcessor());

        pool.complete(event);

        assertTrue(pool.hasFreeProcessor());
        assertEquals(2.0, pool.snapshot().processors().get(0).busyTime());
    }

    @Test
    void shouldReturnImmutableSnapshot() {
        ProcessorPool pool = createPool(2);

        ProcessorPool.Snapshot before = pool.snapshot();

        pool.assign(createRequest(1, 0.0), 0.0);

        ProcessorPool.Snapshot after = pool.snapshot();

        assertNull(before.processors().get(0).currentRequestId());
        assertEquals(1L, after.processors().get(0).currentRequestId());
        assertEquals(2, after.nextProcessorId());

        assertThrows(
            UnsupportedOperationException.class,
            () -> after.processors().clear()
        );
    }

    @Test
    void shouldRejectInvalidPool() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessorPool(List.of())
        );

        ServiceTimeGenerator generator =
            new ServiceTimeGenerator(2.0, 2.0, () -> 0.5);

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessorPool(List.of(
                new Processor(1, generator),
                new Processor(1, generator)
            ))
        );
    }

    @Test
    void shouldProduceReproducibleServiceTimes() {
        RandomStreams firstStreams = new RandomStreams(42);
        RandomStreams secondStreams = new RandomStreams(42);

        ProcessorPool firstPool = new ProcessorPool(List.of(
            new Processor(
                1,
                new ServiceTimeGenerator(
                    1.0,
                    5.0,
                    firstStreams.forProcessor(1)
                )
            )
        ));

        ProcessorPool secondPool = new ProcessorPool(List.of(
            new Processor(
                1,
                new ServiceTimeGenerator(
                    1.0,
                    5.0,
                    secondStreams.forProcessor(1)
                )
            )
        ));

        SimulationEvent first = firstPool.assign(createRequest(1, 0.0), 0.0);
        SimulationEvent second = secondPool.assign(createRequest(1, 0.0), 0.0);

        assertEquals(first.time(), second.time());
        assertTrue(first.time() >= 1.0);
        assertTrue(first.time() <= 5.0);
    }
}
