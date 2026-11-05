package org.example.smo.model;

import java.util.Objects;

public class Request {
    private final long id;
    private final int sourceId;
    private final double createdAt;

    private final String cargo;
    private final String origin;
    private final String destination;

    private RequestStatus status;

    public Request(long id,
                   int sourceId,
                   double createdAt,
                   String cargo,
                   String origin,
                   String destination) {

        if (id <= 0) {
            throw new IllegalArgumentException("Request ID must be positive");
        }

        if (sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        if (!Double.isFinite(createdAt) || createdAt < 0) {
            throw new IllegalArgumentException("Invalid creation time");
        }

        this.id = id;
        this.sourceId = sourceId;
        this.createdAt = createdAt;
        this.cargo = Objects.requireNonNull(cargo);
        this.origin = Objects.requireNonNull(origin);
        this.destination = Objects.requireNonNull(destination);

        this.status = RequestStatus.CREATED;
    }

    public void markQueued() {
        requireStatus(RequestStatus.CREATED);
        status = RequestStatus.QUEUED;
    }

    public void markRejected() {
        requireStatus(RequestStatus.QUEUED);
        status = RequestStatus.REJECTED;
    }

    public void markProcessing() {
        requireStatus(RequestStatus.QUEUED);
        status = RequestStatus.PROCESSING;
    }

    public void markCompleted() {
        requireStatus(RequestStatus.PROCESSING);
        status = RequestStatus.COMPLETED;
    }

    private void requireStatus(RequestStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + ", but was " + status);
        }
    }

    public long getId() {
        return id;
    }

    public int getSourceId() {
        return sourceId;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public String getCargo() {
        return cargo;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public RequestStatus getStatus() {
        return status;
    }
}
