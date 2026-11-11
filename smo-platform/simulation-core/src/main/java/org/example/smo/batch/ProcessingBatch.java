package org.example.smo.batch;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProcessingBatch {
    private final int sourceId;
    private final List<Long> originalRequestIds;
    private final Set<Long> pending;
    private final Map<Long, Integer> running = new LinkedHashMap<>();
    private final Set<Long> completed = new LinkedHashSet<>();
    private final Set<Long> evicted = new LinkedHashSet<>();

    public ProcessingBatch(int sourceId, List<Long> requestIds) {
        if (sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        Objects.requireNonNull(requestIds);

        if (requestIds.isEmpty()) {
            throw new IllegalArgumentException("Batch cannot be empty");
        }

        Set<Long> unique = new HashSet<>();

        for (Long requestId : requestIds) {
            if (requestId == null || requestId <= 0 || !unique.add(requestId)) {
                throw new IllegalArgumentException("Invalid or duplicate request ID");
            }
        }

        this.sourceId = sourceId;
        this.originalRequestIds = List.copyOf(requestIds);
        this.pending = new LinkedHashSet<>(requestIds);
    }

    public Long nextPendingRequestId() {
        return pending.isEmpty() ? null : pending.iterator().next();
    }

    public void markRunning(long requestId, int processorId) {
        if (processorId <= 0 || !pending.remove(requestId)) {
            throw new IllegalStateException("Request is not pending");
        }

        running.put(requestId, processorId);
    }

    public void markCompleted(long requestId) {
        if (running.remove(requestId) == null) {
            throw new IllegalStateException("Request is not running");
        }

        completed.add(requestId);
    }

    public boolean removePendingIfPresent(long requestId) {
        if (!pending.remove(requestId)) {
            return false;
        }

        evicted.add(requestId);
        return true;
    }

    public boolean isFinished() {
        return pending.isEmpty() && running.isEmpty();
    }

    public Snapshot snapshot() {
        return new Snapshot(
            sourceId,
            originalRequestIds,
            List.copyOf(pending),
            Collections.unmodifiableMap(new LinkedHashMap<>(running)),
            List.copyOf(completed),
            List.copyOf(evicted)
        );
    }

    public record Snapshot(
        int sourceId,
        List<Long> originalRequestIds,
        List<Long> pending,
        Map<Long, Integer> running,
        List<Long> completed,
        List<Long> evicted
    ) {}
}
