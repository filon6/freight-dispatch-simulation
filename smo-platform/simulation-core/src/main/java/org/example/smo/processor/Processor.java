package org.example.smo.processor;

import java.util.Objects;

public final class Processor {
    private final int id;
    private final ServiceTimeGenerator serviceTimeGenerator;

    private Long currentRequestId;
    private Double busySince;
    private Double completionTime;
    private double busyTime;

    public Processor(int id, ServiceTimeGenerator serviceTimeGenerator) {
        if (id <= 0) {
            throw new IllegalArgumentException("Processor ID must be positive");
        }

        this.id = id;
        this.serviceTimeGenerator = Objects.requireNonNull(serviceTimeGenerator);
    }

    public int getId() {
        return id;
    }

    public boolean isBusy() {
        return currentRequestId != null;
    }

    double start(long requestId, double time) {
        validateTime(time);

        if (requestId <= 0 || isBusy()) {
            throw new IllegalStateException("Cannot start service");
        }

        double duration = serviceTimeGenerator.sampleServiceTime();
        double endTime = time + duration;

        if (!Double.isFinite(endTime) || endTime < time) {
            throw new IllegalStateException("Invalid service completion time");
        }

        currentRequestId = requestId;
        busySince = time;
        completionTime = endTime;

        return endTime;
    }

    void complete(long requestId, double time) {
        validateTime(time);

        if (!isBusy() || currentRequestId != requestId || Double.compare(time, completionTime) != 0) {
            throw new IllegalStateException("Completion does not match active service");
        }

        double newBusyTime = busyTime + (time - busySince);

        if (!Double.isFinite(newBusyTime)) {
            throw new IllegalStateException("Busy time overflow");
        }

        busyTime = newBusyTime;
        currentRequestId = null;
        busySince = null;
        completionTime = null;
    }

    public Snapshot snapshot() {
        return new Snapshot(
            id,
            currentRequestId,
            busySince,
            completionTime,
            busyTime
        );
    }

    private static void validateTime(double time) {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Time must be finite and non-negative");
        }
    }

    public record Snapshot(
        int id,
        Long currentRequestId,
        Double busySince,
        Double completionTime,
        double busyTime
    ) {}
}
