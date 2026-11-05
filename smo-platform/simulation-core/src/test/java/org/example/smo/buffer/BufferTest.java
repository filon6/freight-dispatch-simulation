package org.example.smo.buffer;

import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BufferTest {
    private Request request(long id, int sourceId, double createdAt) {
        return new Request(id, sourceId, createdAt, "Cargo", "Origin", "Destination");
    }

    @Test
    void usesFirstFreeSlotWithoutShiftingOtherRequests() {
        Buffer buffer = new Buffer(3);
        Request first = request(1, 1, 1.0);
        Request second = request(2, 1, 2.0);
        Request third = request(3, 2, 3.0);

        buffer.place(first);
        buffer.place(second);
        buffer.place(third);

        assertSame(second, buffer.take(2));
        assertEquals(1, buffer.firstFreeSlot());

        PlacementResult result = buffer.place(request(4, 2, 4.0));

        assertEquals(1, result.slotIndex());
        assertNull(result.evictedRequestId());
        assertSame(first, buffer.snapshot().get(0));
        assertSame(third, buffer.snapshot().get(2));
    }

    @Test
    void fullBufferEvictsOldestWaitingRequest() {
        Buffer buffer = new Buffer(3);
        Request first = request(1, 1, 4.0);
        Request oldest = request(2, 2, 1.0);
        Request third = request(3, 1, 2.0);

        buffer.place(first);
        buffer.place(oldest);
        buffer.place(third);

        PlacementResult result = buffer.place(request(4, 2, 5.0));

        assertEquals(1, result.slotIndex());
        assertEquals(Long.valueOf(2), result.evictedRequestId());
        assertSame(oldest, result.evictedRequest());
        assertEquals(3, buffer.size());
        assertEquals(3, buffer.capacity());
    }

    @Test
    void equalArrivalTimesUseRequestIdAsTieBreaker() {
        Buffer buffer = new Buffer(2);

        buffer.place(request(20, 1, 5.0));
        buffer.place(request(10, 2, 5.0));

        PlacementResult result = buffer.place(request(30, 3, 6.0));

        assertEquals(Long.valueOf(10), result.evictedRequestId());
        assertEquals(1, result.slotIndex());
    }

    @Test
    void repeatedTakeDoesNotRemoveAnotherRequest() {
        Buffer buffer = new Buffer(2);
        Request first = request(1, 1, 1.0);
        Request second = request(2, 2, 2.0);

        buffer.place(first);
        buffer.place(second);

        assertSame(first, buffer.take(1));
        assertNull(buffer.take(1));
        assertEquals(1, buffer.size());
        assertSame(second, buffer.snapshot().get(1));
    }

    @Test
    void findsWaitingRequestsBySourceInSlotOrder() {
        Buffer buffer = new Buffer(4);

        buffer.place(request(1, 2, 1.0));
        buffer.place(request(2, 1, 2.0));
        buffer.place(request(3, 1, 3.0));

        assertEquals(List.of(2L, 3L), buffer.getRequestIdsBySource(1));
        assertEquals(List.of(1L), buffer.getRequestIdsBySource(2));
        assertEquals(List.of(), buffer.getRequestIdsBySource(3));
        assertEquals(Set.of(1, 2), buffer.getSourceIdsWithWaitingRequests());
    }

    @Test
    void snapshotIsReadOnlyAndPreservesEmptySlots() {
        Buffer buffer = new Buffer(2);
        buffer.place(request(1, 1, 1.0));

        List<Request> snapshot = buffer.snapshot();

        assertEquals(2, snapshot.size());
        assertNull(snapshot.get(1));

        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.set(0, null)
        );

        buffer.take(1);

        assertNotNull(snapshot.get(0));
        assertNull(buffer.snapshot().get(0));
    }

    @Test
    void processingRequestIsNeverEvicted() {
        Buffer buffer = new Buffer(2);
        Request processing = request(1, 1, 1.0);
        Request waiting = request(2, 1, 2.0);

        buffer.place(processing);
        buffer.place(waiting);

        processing.markQueued();
        processing.markProcessing();
        waiting.markQueued();

        PlacementResult result = buffer.place(request(3, 2, 3.0));

        assertEquals(Long.valueOf(2), result.evictedRequestId());
        assertSame(processing, buffer.snapshot().get(0));
    }

    @Test
    void rejectsReplacementWhenNoWaitingRequestExists() {
        Buffer buffer = new Buffer(1);
        Request processing = request(1, 1, 1.0);

        buffer.place(processing);
        processing.markQueued();
        processing.markProcessing();

        assertThrows(
            IllegalStateException.class,
            () -> buffer.place(request(2, 1, 2.0))
        );

        assertSame(processing, buffer.snapshot().get(0));
    }

    @Test
    void rejectsDuplicateRequestIdWithoutChangingBuffer() {
        Buffer buffer = new Buffer(1);
        Request original = request(1, 1, 1.0);

        buffer.place(original);

        assertThrows(
            IllegalArgumentException.class,
            () -> buffer.place(request(1, 2, 2.0))
        );

        assertSame(original, buffer.snapshot().get(0));
    }

    @Test
    void doesNotChangeRequestStatus() {
        Buffer buffer = new Buffer(1);
        Request first = request(1, 1, 1.0);
        Request second = request(2, 2, 2.0);

        buffer.place(first);
        first.markQueued();

        PlacementResult result = buffer.place(second);

        assertEquals(RequestStatus.QUEUED, result.evictedRequest().getStatus());
        assertEquals(RequestStatus.CREATED, second.getStatus());
    }

    @Test
    void rejectsInvalidCapacityAndNonWaitingRequests() {
        assertThrows(IllegalArgumentException.class, () -> new Buffer(0));

        Buffer buffer = new Buffer(1);
        Request processing = request(1, 1, 1.0);

        processing.markQueued();
        processing.markProcessing();

        assertThrows(
            IllegalArgumentException.class,
            () -> buffer.place(processing)
        );

        assertEquals(0, buffer.size());
    }
}
