package org.example.smo.engine;

import org.example.smo.buffer.Buffer;
import org.example.smo.dispatch.PlacementDispatcher;
import org.example.smo.dispatch.SelectionDispatcher;
import org.example.smo.event.EventCalendar;
import org.example.smo.event.EventType;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Clock;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.model.Source;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.trace.TraceRecorder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class SimulationEngine {
    private final Clock clock = new Clock();
    private final EventCalendar calendar;
    private final Map<Integer, Source> sources = new TreeMap<>();
    private final Map<Long, Request> requests = new TreeMap<>();

    private final Buffer buffer;
    private final ProcessorPool processors;
    private final TraceRecorder trace;
    private final PlacementDispatcher placement;
    private final SelectionDispatcher selection;
    private final long targetRequests;

    private long generatedTotal;
    private boolean initialized;
    private SimulationEvent lastProcessedEvent;

    public SimulationEngine(
        List<Source> sources,
        long targetRequests,
        Map<EventType, Integer> priorities,
        Buffer buffer,
        ProcessorPool processors
    ) {
        Objects.requireNonNull(sources);

        if (sources.isEmpty() || targetRequests <= 0) {
            throw new IllegalArgumentException("Invalid simulation parameters");
        }

        for (Source source : sources) {
            Objects.requireNonNull(source);

            if (this.sources.putIfAbsent(source.getId(), source) != null) {
                throw new IllegalArgumentException("Duplicate source ID");
            }
        }

        this.targetRequests = targetRequests;
        this.buffer = Objects.requireNonNull(buffer);
        this.processors = Objects.requireNonNull(processors);
        this.calendar = new EventCalendar(clock, priorities);
        this.trace = new TraceRecorder(clock);

        this.selection = new SelectionDispatcher(
            buffer,
            processors,
            trace
        );

        this.placement = new PlacementDispatcher(
            buffer,
            trace,
            selection::onRequestEvicted
        );
    }

    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Simulation already initialized");
        }

        for (Source source : sources.values()) {
            scheduleNextArrival(source);
        }

        initialized = true;
    }

    public Request step() {
        requireInitialized();

        if (isFinished()) {
            return null;
        }

        if (calendar.isEmpty()) {
            throw new IllegalStateException("Simulation cannot progress: no events, but requests remain");
        }

        SimulationEvent event = calendar.popEarliest();
        Request affectedRequest;

        switch (event.type()) {
            case ARRIVAL -> affectedRequest = handleArrival(event);
            case SERVICE_COMPLETED -> affectedRequest = handleCompletion(event);
            default -> throw new IllegalStateException("Unknown event type");
        }

        for (SimulationEvent completion : selection.dispatchReady(clock.getCurrentTime())) {
            calendar.schedule(completion);
        }

        lastProcessedEvent = event;
        assertInvariants();

        return affectedRequest;
    }

    public void runToCompletion() {
        requireInitialized();

        while (!isFinished()) {
            step();
        }
    }

    private Request handleArrival(SimulationEvent event) {
        if (generatedTotal >= targetRequests) {
            throw new IllegalStateException("Unexpected arrival after generation limit");
        }

        Source source = sources.get(event.sourceId());

        if (source == null) {
            throw new IllegalStateException("Unknown source ID: " + event.sourceId());
        }

        Request request = source.generateRequest(
            generatedTotal + 1,
            clock.getCurrentTime()
        );

        if (requests.putIfAbsent(request.getId(), request) != null) {
            throw new IllegalStateException("Duplicate request ID");
        }

        generatedTotal++;

        placement.place(request);

        if (generatedTotal == targetRequests) {
            calendar.cancelPendingArrivals();
        } else {
            scheduleNextArrival(source);
        }

        return request;
    }

    private Request handleCompletion(SimulationEvent event) {
        Request request = requests.get(event.requestId());

        if (request == null || request.getStatus() != RequestStatus.PROCESSING) {
            throw new IllegalStateException("Unknown or already completed request: " + event.requestId());
        }

        processors.complete(event);
        request.markCompleted();
        selection.onRequestCompleted(request.getId());

        return request;
    }

    private void scheduleNextArrival(Source source) {
        double time = source.nextArrivalTime(clock.getCurrentTime());

        calendar.schedule(SimulationEvent.arrival(time, source.getId()));
    }

    public void assertInvariants() {
        long completed = 0;
        long rejected = 0;
        long queued = 0;
        long processing = 0;

        for (Request request : requests.values()) {
            switch (request.getStatus()) {
                case COMPLETED -> completed++;
                case REJECTED -> rejected++;
                case QUEUED -> queued++;
                case PROCESSING -> processing++;
                case CREATED -> throw new IllegalStateException("Unplaced request: " + request.getId());
            }
        }

        if (generatedTotal != requests.size() || generatedTotal != completed + rejected + queued + processing) {
            throw new IllegalStateException("Request count invariant violated");
        }

        Set<Long> bufferedIds = new HashSet<>();

        for (Request request : buffer.snapshot()) {
            if (request == null) {
                continue;
            }

            if (request.getStatus() != RequestStatus.QUEUED
                || requests.get(request.getId()) != request
                || !bufferedIds.add(request.getId())) {
                throw new IllegalStateException("Invalid buffered request: " + request.getId());
            }
        }

        Set<Long> processingIds = new HashSet<>();

        for (var state : processors.snapshot().processors()) {
            Long id = state.currentRequestId();

            if (id != null) {
                Request request = requests.get(id);

                if (request == null
                    || request.getStatus() != RequestStatus.PROCESSING
                    || !processingIds.add(id)) {
                    throw new IllegalStateException("Invalid processing request: " + id);
                }
            }
        }

        if (bufferedIds.size() != queued || processingIds.size() != processing) {
            throw new IllegalStateException("Buffer/processor states do not match request statuses");
        }
    }

    public boolean isFinished() {
        if (!initialized || generatedTotal != targetRequests || !calendar.isEmpty()) {
            return false;
        }

        return requests.values().stream().allMatch(request ->
            request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.REJECTED
        );
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Simulation is not initialized");
        }
    }

    public Clock getClock() {
        return clock;
    }

    public EventCalendar getCalendar() {
        return calendar;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public ProcessorPool getProcessors() {
        return processors;
    }

    public TraceRecorder getTrace() {
        return trace;
    }

    public SelectionDispatcher getSelection() {
        return selection;
    }

    public SimulationEvent getLastProcessedEvent() {
        return lastProcessedEvent;
    }

    public Request getRequest(long requestId) {
        return requests.get(requestId);
    }

    public List<Request> getRequests() {
        return List.copyOf(requests.values());
    }

    public long getGeneratedTotal() {
        return generatedTotal;
    }
}
