package org.example.smo.stats;

import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class StatisticsCollector {
    private final Map<Long, RequestData> requests = new TreeMap<>();
    private final Map<Integer, SourceData> sources = new TreeMap<>();
    private final Map<Integer, Double> processorBusyTimes = new TreeMap<>();

    public StatisticsCollector(
        Collection<Integer> sourceIds,
        Collection<Integer> processorIds
    ) {
        Objects.requireNonNull(sourceIds);
        Objects.requireNonNull(processorIds);

        for (Integer id : sourceIds) {
            if (id == null || id <= 0 || sources.putIfAbsent(id, new SourceData()) != null) {
                throw new IllegalArgumentException("Invalid or duplicate source ID");
            }
        }

        for (Integer id : processorIds) {
            if (id == null || id <= 0 || processorBusyTimes.putIfAbsent(id, 0.0) != null) {
                throw new IllegalArgumentException("Invalid or duplicate processor ID");
            }
        }
    }

    public void recordGenerated(Request request) {
        Objects.requireNonNull(request);

        SourceData source = sources.get(request.getSourceId());

        if (source == null || request.getStatus() != RequestStatus.CREATED) {
            throw new IllegalArgumentException("Unknown source or invalid request status");
        }

        if (requests.containsKey(request.getId())) {
            throw new IllegalStateException("Request already recorded: " + request.getId());
        }

        requests.put(request.getId(), new RequestData(request));
        source.generated++;
    }

    public void recordQueued(long requestId, double at) {
        RequestData data = requireStatus(requestId, RequestStatus.CREATED);
        validateTime(at, data.arrivalAt);

        data.queuedAt = at;
        data.status = RequestStatus.QUEUED;
    }

    public void recordRejected(long requestId, double at) {
        RequestData data = requireStatus(requestId, RequestStatus.QUEUED);
        validateTime(at, data.queuedAt);

        data.rejectedAt = at;
        data.status = RequestStatus.REJECTED;
        sources.get(data.sourceId).rejected++;
    }

    public void recordServiceStart(long requestId, int processorId, double at) {
        RequestData data = requireStatus(requestId, RequestStatus.QUEUED);

        if (!processorBusyTimes.containsKey(processorId)) {
            throw new IllegalArgumentException("Unknown processor ID: " + processorId);
        }

        validateTime(at, data.queuedAt);

        data.serviceStartedAt = at;
        data.processorId = processorId;
        data.status = RequestStatus.PROCESSING;
    }

    public void recordCompleted(long requestId, double at) {
        RequestData data = requireStatus(requestId, RequestStatus.PROCESSING);
        validateTime(at, data.serviceStartedAt);

        double wait = data.serviceStartedAt - data.arrivalAt;
        double service = at - data.serviceStartedAt;
        double stay = at - data.arrivalAt;
        double busyTime = processorBusyTimes.get(data.processorId) + service;

        if (!Double.isFinite(wait) || !Double.isFinite(service)
            || !Double.isFinite(stay) || !Double.isFinite(busyTime)) {
            throw new IllegalStateException("Statistics overflow");
        }

        data.completedAt = at;
        data.status = RequestStatus.COMPLETED;
        processorBusyTimes.put(data.processorId, busyTime);

        SourceData source = sources.get(data.sourceId);
        source.completed++;
        source.waiting.add(wait);
        source.service.add(service);
        source.stay.add(stay);
    }

    public RequestSnapshot requestSnapshot(long requestId) {
        RequestData data = requests.get(requestId);
        return data == null ? null : data.snapshot();
    }

    public Map<Long, RequestSnapshot> requestSnapshots() {
        Map<Long, RequestSnapshot> result = new TreeMap<>();

        requests.forEach((id, data) -> result.put(id, data.snapshot()));

        return Collections.unmodifiableMap(result);
    }

    public Map<Integer, SourceSnapshot> sourceSnapshots() {
        Map<Integer, SourceSnapshot> result = new TreeMap<>();

        sources.forEach((id, source) -> result.put(id, new SourceSnapshot(
            id,
            source.generated,
            source.rejected,
            source.completed,
            source.generated == 0 ? 0.0 : (double) source.rejected / source.generated,
            source.stay.meanOrNull(),
            source.waiting.meanOrNull(),
            source.service.meanOrNull(),
            source.waiting.varianceOrNull(),
            source.service.varianceOrNull()
        )));

        return Collections.unmodifiableMap(result);
    }

    public Map<Integer, ProcessorSnapshot> processorSnapshots(double totalTime) {
        validateTime(totalTime, 0.0);

        Map<Integer, ProcessorSnapshot> result = new TreeMap<>();

        processorBusyTimes.forEach((id, busyTime) -> result.put(id, new ProcessorSnapshot(
            id,
            busyTime,
            totalTime == 0.0 ? 0.0 : busyTime / totalTime
        )));

        return Collections.unmodifiableMap(result);
    }

    private RequestData requireStatus(long requestId, RequestStatus expected) {
        RequestData data = requests.get(requestId);

        if (data == null || data.status != expected) {
            throw new IllegalStateException("Request " + requestId + " must have status " + expected);
        }

        return data;
    }

    private static void validateTime(double at, double earliest) {
        if (!Double.isFinite(at) || at < earliest) {
            throw new IllegalArgumentException("Invalid event time");
        }
    }

    public record RequestSnapshot(
        long requestId,
        int sourceId,
        double arrivalAt,
        Double queuedAt,
        Double rejectedAt,
        Double serviceStartedAt,
        Double completedAt,
        Integer processorId,
        RequestStatus status,
        Double waitingTime,
        Double serviceTime,
        Double stayTime
    ) {}

    public record SourceSnapshot(
        int sourceId,
        long generated,
        long rejected,
        long completed,
        double rejectionProbability,
        Double meanStay,
        Double meanWaiting,
        Double meanService,
        Double varianceWaiting,
        Double varianceService
    ) {}

    public record ProcessorSnapshot(
        int processorId,
        double busyTime,
        double utilization
    ) {}

    private static final class RequestData {
        private final long requestId;
        private final int sourceId;
        private final double arrivalAt;

        private Double queuedAt;
        private Double rejectedAt;
        private Double serviceStartedAt;
        private Double completedAt;
        private Integer processorId;
        private RequestStatus status = RequestStatus.CREATED;

        private RequestData(Request request) {
            requestId = request.getId();
            sourceId = request.getSourceId();
            arrivalAt = request.getCreatedAt();
        }

        private RequestSnapshot snapshot() {
            Double waiting = null;
            Double service = null;
            Double stay = null;

            if (completedAt != null) {
                waiting = serviceStartedAt - arrivalAt;
                service = completedAt - serviceStartedAt;
                stay = completedAt - arrivalAt;
            }

            return new RequestSnapshot(
                requestId,
                sourceId,
                arrivalAt,
                queuedAt,
                rejectedAt,
                serviceStartedAt,
                completedAt,
                processorId,
                status,
                waiting,
                service,
                stay
            );
        }
    }

    private static final class SourceData {
        private long generated;
        private long rejected;
        private long completed;

        private final Moments waiting = new Moments();
        private final Moments service = new Moments();
        private final Moments stay = new Moments();
    }

    private static final class Moments {
        private long count;
        private double mean;
        private double squaredDifferences;

        private void add(double value) {
            count++;

            double delta = value - mean;
            mean += delta / count;
            squaredDifferences += delta * (value - mean);
        }

        private Double meanOrNull() {
            return count == 0 ? null : mean;
        }

        private Double varianceOrNull() {
            return count == 0
                ? null
                : Math.max(0.0, squaredDifferences / count);
        }
    }
}
