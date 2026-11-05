package org.example.smo.trace;

import org.example.smo.model.Clock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceRecorderTest {
    @Test
    void shouldRecordActionsAtCurrentModelTime() {
        Clock clock = new Clock();
        TraceRecorder recorder = new TraceRecorder(clock);

        clock.advanceTo(2.5);

        recorder.record(TraceAction.ENQUEUED, 1L, 1, null);
        recorder.record(TraceAction.SELECTED, 1L, 1, null);
        recorder.record(TraceAction.ASSIGNED, 1L, 1, 2);

        List<TraceEntry> entries = recorder.snapshot();

        assertEquals(3, entries.size());

        assertEquals(2.5, entries.get(0).time());
        assertEquals(2.5, entries.get(1).time());
        assertEquals(2.5, entries.get(2).time());

        assertEquals(TraceAction.ENQUEUED, entries.get(0).action());
        assertEquals(TraceAction.SELECTED, entries.get(1).action());
        assertEquals(TraceAction.ASSIGNED, entries.get(2).action());

        assertEquals(2, entries.get(2).processorId());
    }

    @Test
    void shouldPreserveTransitionOrder() {
        Clock clock = new Clock();
        TraceRecorder recorder = new TraceRecorder(clock);

        recorder.record(TraceAction.ENQUEUED, 1L, 1, null);

        clock.advanceTo(3.0);

        recorder.record(TraceAction.EVICTED, 1L, 1, null);

        List<TraceEntry> entries = recorder.snapshot();

        assertEquals(2, entries.size());
        assertEquals(0.0, entries.get(0).time());
        assertEquals(3.0, entries.get(1).time());

        assertEquals(TraceAction.ENQUEUED, entries.get(0).action());
        assertEquals(TraceAction.EVICTED, entries.get(1).action());
    }

    @Test
    void shouldReturnImmutableSnapshot() {
        TraceRecorder recorder = new TraceRecorder(new Clock());

        recorder.record(TraceAction.ENQUEUED, 1L, 1, null);

        List<TraceEntry> snapshot = recorder.snapshot();

        assertThrows(
            UnsupportedOperationException.class,
            snapshot::clear
        );

        assertEquals(1, recorder.snapshot().size());
    }
}
