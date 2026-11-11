package org.example.smo.batch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingBatchTest {
    @Test
    void shouldTrackPendingRunningAndCompleted() {
        ProcessingBatch batch = new ProcessingBatch(1, List.of(10L, 11L));

        assertEquals(Long.valueOf(10L), batch.nextPendingRequestId());
        assertFalse(batch.isFinished());

        batch.markRunning(10L, 2);

        assertEquals(Long.valueOf(11L), batch.nextPendingRequestId());
        assertEquals(Integer.valueOf(2), batch.snapshot().running().get(10L));

        batch.markRunning(11L, 1);

        assertNull(batch.nextPendingRequestId());
        assertFalse(batch.isFinished());

        batch.markCompleted(10L);
        batch.markCompleted(11L);

        assertTrue(batch.isFinished());
        assertEquals(List.of(10L, 11L), batch.snapshot().completed());
    }

    @Test
    void shouldKeepFixedOriginalSetAfterEviction() {
        ProcessingBatch batch = new ProcessingBatch(1, List.of(1L, 2L, 3L));
        batch.markRunning(1L, 1);

        assertTrue(batch.removePendingIfPresent(2L));
        assertFalse(batch.removePendingIfPresent(2L));
        assertFalse(batch.removePendingIfPresent(1L));

        assertEquals(List.of(1L, 2L, 3L), batch.snapshot().originalRequestIds());
        assertEquals(List.of(3L), batch.snapshot().pending());
        assertEquals(List.of(2L), batch.snapshot().evicted());
    }

    @Test
    void shouldFinishWhenLastPendingIsEvicted() {
        ProcessingBatch batch = new ProcessingBatch(1, List.of(1L));

        assertTrue(batch.removePendingIfPresent(1L));
        assertTrue(batch.isFinished());
        assertEquals(List.of(1L), batch.snapshot().evicted());
    }

    @Test
    void shouldRejectInvalidTransitions() {
        ProcessingBatch batch = new ProcessingBatch(1, List.of(1L));

        assertThrows(IllegalStateException.class, () -> batch.markCompleted(1L));
        assertThrows(IllegalStateException.class, () -> batch.markRunning(2L, 1));
        assertThrows(IllegalStateException.class, () -> batch.markRunning(1L, 0));

        batch.markRunning(1L, 1);

        assertThrows(IllegalStateException.class, () -> batch.markRunning(1L, 1));

        batch.markCompleted(1L);

        assertThrows(IllegalStateException.class, () -> batch.markCompleted(1L));
    }

    @Test
    void snapshotShouldBeIndependentAndReadOnly() {
        ProcessingBatch batch = new ProcessingBatch(1, List.of(1L, 2L));
        ProcessingBatch.Snapshot before = batch.snapshot();

        batch.markRunning(1L, 1);

        assertEquals(List.of(1L, 2L), before.pending());
        assertEquals(List.of(2L), batch.snapshot().pending());

        assertThrows(
            UnsupportedOperationException.class,
            () -> before.pending().clear()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> before.originalRequestIds().clear()
        );
    }

    @Test
    void shouldRejectInvalidBatch() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessingBatch(0, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingBatch(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingBatch(1, List.of(1L, 1L)));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingBatch(1, List.of(-1L)));
    }
}
