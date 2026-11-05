package org.example.smo.event;

import java.util.Objects;

public record SimulationEvent(
    double time,
    EventType type,
    Long requestId,
    Integer sourceId,
    Integer processorId,
    long sequenceNo
) {
    public SimulationEvent {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Event time must be finite and non-negative");
        }

        Objects.requireNonNull(type, "Event type must not be null");

        if (sequenceNo < 0) {
            throw new IllegalArgumentException("Sequence number cannot be negative");
        }

        if (sourceId != null && sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        if (requestId != null && requestId <= 0) {
            throw new IllegalArgumentException("Request ID must be positive");
        }

        if (processorId != null && processorId <= 0) {
            throw new IllegalArgumentException("Processor ID must be positive");
        }

        switch (type) {
            case ARRIVAL -> {
                if (sourceId == null || requestId != null || processorId != null) {
                    throw new IllegalArgumentException("ARRIVAL requires source ID only");
                }
            }

            case SERVICE_COMPLETED -> {
                if (requestId == null || processorId == null) {
                    throw new IllegalArgumentException("SERVICE_COMPLETED requires request ID and processor ID");
                }
            }
        }
    }

    public static SimulationEvent arrival(double time, int sourceId) {
        return new SimulationEvent(
            time,
            EventType.ARRIVAL,
            null,
            sourceId,
            null,
            0
        );
    }

    public static SimulationEvent serviceCompleted(
        double time,
        long requestId,
        Integer sourceId,
        int processorId
    ) {
        return new SimulationEvent(
            time,
            EventType.SERVICE_COMPLETED,
            requestId,
            sourceId,
            processorId,
            0
        );
    }

    SimulationEvent withSequenceNo(long value) {
        return new SimulationEvent(
            time,
            type,
            requestId,
            sourceId,
            processorId,
            value
        );
    }
}
