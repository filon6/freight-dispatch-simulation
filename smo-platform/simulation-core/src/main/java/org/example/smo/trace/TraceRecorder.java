package org.example.smo.trace;

import org.example.smo.model.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TraceRecorder {
    private final Clock clock;
    private final List<TraceEntry> entries = new ArrayList<>();

    public TraceRecorder(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public void record(
        TraceAction action,
        long requestId,
        Integer sourceId,
        Integer processorId
    ) {
        entries.add(new TraceEntry(
            clock.getCurrentTime(),
            action,
            requestId,
            sourceId,
            processorId
        ));
    }

    public List<TraceEntry> snapshot() {
        return List.copyOf(entries);
    }
}
