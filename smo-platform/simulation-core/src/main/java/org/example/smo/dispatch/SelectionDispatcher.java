package org.example.smo.dispatch;

import org.example.smo.batch.ProcessingBatch;
import org.example.smo.buffer.Buffer;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.trace.TraceAction;
import org.example.smo.trace.TraceRecorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SelectionDispatcher {
    private final Buffer buffer;
    private final ProcessorPool processors;
    private final TraceRecorder trace;

    private ProcessingBatch activeBatch;

    public SelectionDispatcher(
        Buffer buffer,
        ProcessorPool processors,
        TraceRecorder trace
    ) {
        this.buffer = Objects.requireNonNull(buffer);
        this.processors = Objects.requireNonNull(processors);
        this.trace = Objects.requireNonNull(trace);
    }

    public List<SimulationEvent> dispatchReady(double time) {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Invalid dispatch time");
        }

        List<SimulationEvent> completions = new ArrayList<>();

        while (processors.hasFreeProcessor()) {
            if (activeBatch == null || activeBatch.isFinished()) {
                activeBatch = createNextBatch();
            }

            if (activeBatch == null) {
                break;
            }

            Long pendingRequestId = activeBatch.nextPendingRequestId();

            if (pendingRequestId == null) {
                break;
            }

            long requestId = pendingRequestId;
            Request request = buffer.take(requestId);

            if (request == null || request.getStatus() != RequestStatus.QUEUED) {
                throw new IllegalStateException("Pending request is missing from the buffer");
            }

            SimulationEvent completion = processors.assign(request, time);

            if (completion == null) {
                throw new IllegalStateException("Free processor was not found");
            }

            activeBatch.markRunning(requestId, completion.processorId());

            trace.record(
                TraceAction.SELECTED,
                requestId,
                request.getSourceId(),
                null
            );

            trace.record(
                TraceAction.ASSIGNED,
                requestId,
                request.getSourceId(),
                completion.processorId()
            );

            completions.add(completion);
        }

        return List.copyOf(completions);
    }

    public void onRequestEvicted(long requestId) {
        if (activeBatch != null) {
            activeBatch.removePendingIfPresent(requestId);
        }
    }

    public void onRequestCompleted(long requestId) {
        if (activeBatch == null) {
            throw new IllegalStateException("No active batch");
        }

        activeBatch.markCompleted(requestId);
    }

    public ProcessingBatch.Snapshot activeBatchSnapshot() {
        return activeBatch == null ? null : activeBatch.snapshot();
    }

    private ProcessingBatch createNextBatch() {
        Integer sourceId = buffer.getSourceIdsWithWaitingRequests()
            .stream()
            .min(Integer::compareTo)
            .orElse(null);

        if (sourceId == null) {
            return null;
        }

        List<Long> requestIds = buffer.snapshot().stream()
            .filter(Objects::nonNull)
            .filter(request -> request.getStatus() == RequestStatus.QUEUED)
            .filter(request -> request.getSourceId() == sourceId)
            .sorted(Comparator.comparingDouble(Request::getCreatedAt).thenComparingLong(Request::getId))
            .map(Request::getId)
            .toList();

        return new ProcessingBatch(sourceId, requestIds);
    }
}
