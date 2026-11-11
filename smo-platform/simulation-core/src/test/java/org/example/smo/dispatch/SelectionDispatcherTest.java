package org.example.smo.dispatch;

import org.example.smo.buffer.Buffer;
import org.example.smo.buffer.PlacementResult;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Clock;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.processor.Processor;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.processor.ServiceTimeGenerator;
import org.example.smo.trace.TraceAction;
import org.example.smo.trace.TraceRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SelectionDispatcherTest {
    private record Fixture(
        Clock clock,
        Buffer buffer,
        ProcessorPool pool,
        TraceRecorder trace,
        SelectionDispatcher selection,
        PlacementDispatcher placement
    ) {}

    private Fixture fixture(int capacity, int processorCount) {
        Clock clock = new Clock();
        Buffer buffer = new Buffer(capacity);
        TraceRecorder trace = new TraceRecorder(clock);

        List<Processor> processors = IntStream.rangeClosed(1, processorCount)
            .mapToObj(id -> new Processor(
                id,
                new ServiceTimeGenerator(2.0, 2.0, () -> 0.5)
            ))
            .toList();

        ProcessorPool pool = new ProcessorPool(processors);
        SelectionDispatcher selection = new SelectionDispatcher(buffer, pool, trace);

        PlacementDispatcher placement = new PlacementDispatcher(
            buffer,
            trace,
            selection::onRequestEvicted
        );

        return new Fixture(clock, buffer, pool, trace, selection, placement);
    }

    private Request enqueue(Fixture fixture, long id, int sourceId, double time) {
        Request request = new Request(
            id,
            sourceId,
            time,
            "Cargo",
            "Origin",
            "Destination"
        );

        fixture.placement().place(request);

        return request;
    }

    @Test
    void shouldChooseHighestPrioritySourceAndArrivalOrder() {
        Fixture fixture = fixture(4, 2);

        Request otherSource = enqueue(fixture, 1, 2, 0.0);
        Request later = enqueue(fixture, 2, 1, 0.5);
        Request earlier = enqueue(fixture, 3, 1, 0.2);

        fixture.clock().advanceTo(1.0);

        List<SimulationEvent> events = fixture.selection().dispatchReady(1.0);

        assertEquals(2, events.size());

        assertEquals(Long.valueOf(3L), events.get(0).requestId());
        assertEquals(Long.valueOf(2L), events.get(1).requestId());

        assertEquals(Integer.valueOf(1), events.get(0).processorId());
        assertEquals(Integer.valueOf(2), events.get(1).processorId());

        assertEquals(
            List.of(3L, 2L),
            fixture.selection().activeBatchSnapshot().originalRequestIds()
        );

        assertEquals(RequestStatus.PROCESSING, earlier.getStatus());
        assertEquals(RequestStatus.PROCESSING, later.getStatus());
        assertEquals(RequestStatus.QUEUED, otherSource.getStatus());

        assertEquals(
            4L,
            fixture.trace().snapshot().stream()
                .filter(entry -> entry.action() == TraceAction.SELECTED
                    || entry.action() == TraceAction.ASSIGNED)
                .count()
        );
    }

    @Test
    void shouldNotAddNewArrivalsToActiveBatch() {
        Fixture fixture = fixture(4, 2);

        Request first = enqueue(fixture, 1, 1, 0.0);
        enqueue(fixture, 2, 2, 0.0);

        fixture.clock().advanceTo(1.0);

        SimulationEvent completion = fixture.selection().dispatchReady(1.0).get(0);

        fixture.clock().advanceTo(1.5);
        Request newRequest = enqueue(fixture, 3, 1, 1.5);

        assertTrue(fixture.selection().dispatchReady(1.5).isEmpty());

        assertEquals(
            List.of(1L),
            fixture.selection().activeBatchSnapshot().originalRequestIds()
        );

        assertEquals(RequestStatus.QUEUED, newRequest.getStatus());

        fixture.clock().advanceTo(completion.time());
        fixture.pool().complete(completion);
        first.markCompleted();
        fixture.selection().onRequestCompleted(first.getId());

        List<SimulationEvent> next = fixture.selection().dispatchReady(completion.time());

        assertEquals(1, next.size());
        assertEquals(Long.valueOf(3L), next.get(0).requestId());
    }

    @Test
    void shouldRemoveEvictedPendingRequestAndSwitchOnlyAfterCompletion() {
        Fixture fixture = fixture(2, 1);

        Request running = enqueue(fixture, 1, 1, 0.0);
        Request pending = enqueue(fixture, 2, 1, 0.1);

        fixture.clock().advanceTo(1.0);

        SimulationEvent completion = fixture.selection().dispatchReady(1.0).get(0);

        fixture.clock().advanceTo(1.1);
        enqueue(fixture, 3, 2, 1.1);

        fixture.clock().advanceTo(1.2);

        PlacementResult result = fixture.placement().place(new Request(
            4,
            2,
            1.2,
            "Cargo",
            "Origin",
            "Destination"
        ));

        assertEquals(Long.valueOf(2L), result.evictedRequestId());
        assertEquals(RequestStatus.REJECTED, pending.getStatus());

        assertEquals(
            List.of(1L, 2L),
            fixture.selection().activeBatchSnapshot().originalRequestIds()
        );

        assertEquals(
            List.of(2L),
            fixture.selection().activeBatchSnapshot().evicted()
        );

        assertTrue(fixture.selection().activeBatchSnapshot().pending().isEmpty());
        assertTrue(fixture.selection().dispatchReady(1.2).isEmpty());

        fixture.clock().advanceTo(completion.time());
        fixture.pool().complete(completion);
        running.markCompleted();
        fixture.selection().onRequestCompleted(running.getId());

        List<SimulationEvent> next = fixture.selection().dispatchReady(completion.time());

        assertEquals(1, next.size());
        assertEquals(Long.valueOf(3L), next.get(0).requestId());
        assertEquals(2, fixture.selection().activeBatchSnapshot().sourceId());
    }

    @Test
    void shouldKeepFreeProcessorIdleWhileBatchIsRunning() {
        Fixture fixture = fixture(3, 2);

        Request running = enqueue(fixture, 1, 1, 0.0);
        Request other = enqueue(fixture, 2, 2, 0.0);

        fixture.clock().advanceTo(1.0);

        SimulationEvent completion = fixture.selection().dispatchReady(1.0).get(0);

        assertTrue(fixture.pool().hasFreeProcessor());
        assertTrue(fixture.selection().dispatchReady(1.0).isEmpty());
        assertEquals(RequestStatus.QUEUED, other.getStatus());

        fixture.clock().advanceTo(completion.time());
        fixture.pool().complete(completion);
        running.markCompleted();
        fixture.selection().onRequestCompleted(1L);

        assertEquals(
            Long.valueOf(2L),
            fixture.selection().dispatchReady(completion.time()).get(0).requestId()
        );
    }

    @Test
    void shouldDoNothingWhenBufferIsEmpty() {
        Fixture fixture = fixture(2, 1);

        assertTrue(fixture.selection().dispatchReady(0.0).isEmpty());
        assertNull(fixture.selection().activeBatchSnapshot());
        assertTrue(fixture.trace().snapshot().isEmpty());
    }
}
