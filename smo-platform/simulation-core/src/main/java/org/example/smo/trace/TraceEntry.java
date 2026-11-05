package org.example.smo.trace;

import java.util.Objects;

public record TraceEntry(
    double time,
    TraceAction action,
    long requestId,
    Integer sourceId,
    Integer processorId
) {
    public TraceEntry {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Invalid trace time");
        }

        Objects.requireNonNull(action);

        if (requestId <= 0) {
            throw new IllegalArgumentException("Request ID must be positive");
        }

        if (sourceId != null && sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        if (processorId != null && processorId <= 0) {
            throw new IllegalArgumentException("Processor ID must be positive");
        }
    }
}
