package org.example.smo.dispatch;

import org.example.smo.buffer.Buffer;
import org.example.smo.buffer.PlacementResult;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.trace.TraceAction;
import org.example.smo.trace.TraceRecorder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

public final class PlacementDispatcher {
    private final Buffer buffer;
    private final TraceRecorder trace;
    private final LongConsumer onEvicted;

    private final Set<Long> processedRequestIds = new HashSet<>();

    public PlacementDispatcher(
        Buffer buffer,
        TraceRecorder trace,
        LongConsumer onEvicted
    ) {
        this.buffer = Objects.requireNonNull(buffer);
        this.trace = Objects.requireNonNull(trace);
        this.onEvicted = Objects.requireNonNull(onEvicted);
    }

    public PlacementResult place(Request request) {
        Objects.requireNonNull(request);

        if (processedRequestIds.contains(request.getId())) {
            throw new IllegalStateException("Request already processed: " + request.getId());
        }

        if (request.getStatus() != RequestStatus.CREATED) {
            throw new IllegalArgumentException("Only CREATED requests can be placed");
        }

        PlacementResult result = buffer.place(request);
        Request evicted = result.evictedRequest();

        if (evicted != null) {
            evicted.markRejected();

            trace.record(
                TraceAction.EVICTED,
                evicted.getId(),
                evicted.getSourceId(),
                null
            );
        }

        request.markQueued();

        trace.record(
            TraceAction.ENQUEUED,
            request.getId(),
            request.getSourceId(),
            null
        );

        processedRequestIds.add(request.getId());

        if (evicted != null) {
            onEvicted.accept(evicted.getId());
        }

        return result;
    }
}
