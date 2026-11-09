package org.example.smo.dispatch;

import org.example.smo.buffer.Buffer;
import org.example.smo.buffer.PlacementResult;
import org.example.smo.model.Clock;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.trace.TraceAction;
import org.example.smo.trace.TraceRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlacementDispatcherTest {
    private Request request(long id, int sourceId, double time) {
        return new Request(id, sourceId, time, "Cargo", "Origin", "Destination");
    }

    @Test
    void shouldPlaceRequestAndMarkItQueued() {
        Buffer buffer = new Buffer(2);
        TraceRecorder trace = new TraceRecorder(new Clock());
        List<Long> evictedIds = new ArrayList<>();

        PlacementDispatcher dispatcher = new PlacementDispatcher(
            buffer,
            trace,
            id -> evictedIds.add(id)
        );

        Request request = request(1, 1, 1.0);
        PlacementResult result = dispatcher.place(request);

        assertEquals(0, result.slotIndex());
        assertNull(result.evictedRequest());
        assertEquals(RequestStatus.QUEUED, request.getStatus());
        assertSame(request, buffer.snapshot().get(0));

        assertEquals(1, trace.snapshot().size());
        assertEquals(TraceAction.ENQUEUED, trace.snapshot().get(0).action());
        assertTrue(evictedIds.isEmpty());
    }

    @Test
    void shouldRejectOldestAndNotifyExactlyOnce() {
        Buffer buffer = new Buffer(2);
        TraceRecorder trace = new TraceRecorder(new Clock());
        List<Long> evictedIds = new ArrayList<>();

        PlacementDispatcher dispatcher = new PlacementDispatcher(
            buffer,
            trace,
            id -> evictedIds.add(id)
        );

        Request oldest = request(1, 1, 1.0);
        Request second = request(2, 2, 2.0);
        Request incoming = request(3, 1, 3.0);

        dispatcher.place(oldest);
        dispatcher.place(second);

        PlacementResult result = dispatcher.place(incoming);

        assertSame(oldest, result.evictedRequest());
        assertEquals(0, result.slotIndex());

        assertEquals(RequestStatus.REJECTED, oldest.getStatus());
        assertEquals(RequestStatus.QUEUED, incoming.getStatus());

        assertSame(incoming, buffer.snapshot().get(0));
        assertSame(second, buffer.snapshot().get(1));

        assertEquals(List.of(1L), evictedIds);

        assertEquals(4, trace.snapshot().size());
        assertEquals(TraceAction.EVICTED, trace.snapshot().get(2).action());
        assertEquals(1L, trace.snapshot().get(2).requestId());
        assertEquals(TraceAction.ENQUEUED, trace.snapshot().get(3).action());
        assertEquals(3L, trace.snapshot().get(3).requestId());
    }

    @Test
    void shouldNotifyActiveBatchWithoutDependingOnItsClass() {
        Buffer buffer = new Buffer(1);
        TraceRecorder trace = new TraceRecorder(new Clock());
        Set<Long> pendingRequestIds = new HashSet<>(Set.of(1L));

        PlacementDispatcher dispatcher = new PlacementDispatcher(
            buffer,
            trace,
            id -> pendingRequestIds.remove(id)
        );

        dispatcher.place(request(1, 1, 1.0));
        dispatcher.place(request(2, 2, 2.0));

        assertFalse(pendingRequestIds.contains(1L));
    }

    @Test
    void shouldRejectDuplicateWithoutAdditionalSideEffects() {
        Buffer buffer = new Buffer(1);
        TraceRecorder trace = new TraceRecorder(new Clock());
        List<Long> evictedIds = new ArrayList<>();

        PlacementDispatcher dispatcher = new PlacementDispatcher(
            buffer,
            trace,
            id -> evictedIds.add(id)
        );

        Request first = request(1, 1, 1.0);
        dispatcher.place(first);

        assertThrows(
            IllegalStateException.class,
            () -> dispatcher.place(first)
        );

        assertThrows(
            IllegalStateException.class,
            () -> dispatcher.place(request(1, 2, 2.0))
        );

        assertSame(first, buffer.snapshot().get(0));
        assertEquals(1, trace.snapshot().size());
        assertTrue(evictedIds.isEmpty());
    }

    @Test
    void shouldNotReuseEvictedRequestId() {
        Buffer buffer = new Buffer(1);
        TraceRecorder trace = new TraceRecorder(new Clock());
        List<Long> evictedIds = new ArrayList<>();

        PlacementDispatcher dispatcher = new PlacementDispatcher(
            buffer,
            trace,
            id -> evictedIds.add(id)
        );

        dispatcher.place(request(1, 1, 1.0));

        Request second = request(2, 1, 2.0);
        dispatcher.place(second);

        assertThrows(
            IllegalStateException.class,
            () -> dispatcher.place(request(1, 1, 3.0))
        );

        assertSame(second, buffer.snapshot().get(0));
        assertEquals(List.of(1L), evictedIds);
        assertEquals(3, trace.snapshot().size());
    }

    @Test
    void shouldRejectRequestThatIsNotCreated() {
        Buffer buffer = new Buffer(1);
        TraceRecorder trace = new TraceRecorder(new Clock());

        PlacementDispatcher dispatcher = new PlacementDispatcher(
            buffer,
            trace,
            id -> {}
        );

        Request request = request(1, 1, 1.0);
        request.markQueued();

        assertThrows(
            IllegalArgumentException.class,
            () -> dispatcher.place(request)
        );

        assertNull(buffer.snapshot().get(0));
        assertTrue(trace.snapshot().isEmpty());
    }
}
