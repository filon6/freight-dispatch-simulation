package org.example.smo.stats;

import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsCollectorTest {
    private Request request(long id, int sourceId, double arrivalAt) {
        return new Request(
            id,
            sourceId,
            arrivalAt,
            "Cargo",
            "Origin",
            "Destination"
        );
    }

    private StatisticsCollector collector() {
        return new StatisticsCollector(
            List.of(1, 2),
            List.of(1, 2)
        );
    }

    private void enqueue(StatisticsCollector stats, Request request) {
        stats.recordGenerated(request);
        request.markQueued();
        stats.recordQueued(request.getId(), request.getCreatedAt());
    }

    @Test
    void shouldCalculateWaitingServiceAndStayTimes() {
        StatisticsCollector stats = collector();

        Request request = request(1, 1, 2.0);
        enqueue(stats, request);

        request.markProcessing();
        stats.recordServiceStart(1L, 1, 5.0);

        request.markCompleted();
        stats.recordCompleted(1L, 9.0);

        StatisticsCollector.RequestSnapshot result = stats.requestSnapshot(1L);

        assertEquals(RequestStatus.COMPLETED, result.status());
        assertEquals(2.0, result.arrivalAt());
        assertEquals(5.0, result.serviceStartedAt());
        assertEquals(9.0, result.completedAt());

        assertEquals(3.0, result.waitingTime());
        assertEquals(4.0, result.serviceTime());
        assertEquals(7.0, result.stayTime());

        assertEquals(4.0, stats.processorSnapshots(9.0).get(1).busyTime());
    }

    @Test
    void shouldCalculateMeansAndVariances() {
        StatisticsCollector stats = collector();

        Request first = request(1, 1, 0.0);
        enqueue(stats, first);
        first.markProcessing();
        stats.recordServiceStart(1L, 1, 1.0);
        first.markCompleted();
        stats.recordCompleted(1L, 4.0);

        Request second = request(2, 1, 1.0);
        enqueue(stats, second);
        second.markProcessing();
        stats.recordServiceStart(2L, 1, 4.0);
        second.markCompleted();
        stats.recordCompleted(2L, 6.0);

        StatisticsCollector.SourceSnapshot source = stats.sourceSnapshots().get(1);

        assertEquals(2, source.generated());
        assertEquals(2, source.completed());
        assertEquals(0, source.rejected());

        assertEquals(2.0, source.meanWaiting());
        assertEquals(2.5, source.meanService());
        assertEquals(4.5, source.meanStay());

        assertEquals(1.0, source.varianceWaiting());
        assertEquals(0.25, source.varianceService());

        StatisticsCollector.ProcessorSnapshot processor =
            stats.processorSnapshots(6.0).get(1);

        assertEquals(5.0, processor.busyTime());
        assertEquals(5.0 / 6.0, processor.utilization(), 1e-12);
    }

    @Test
    void shouldHandleAllRejectedAndEmptySource() {
        StatisticsCollector stats = collector();

        Request first = request(1, 1, 1.0);
        Request second = request(2, 1, 2.0);

        enqueue(stats, first);
        enqueue(stats, second);

        first.markRejected();
        stats.recordRejected(1L, 3.0);

        second.markRejected();
        stats.recordRejected(2L, 4.0);

        StatisticsCollector.SourceSnapshot source1 = stats.sourceSnapshots().get(1);
        StatisticsCollector.SourceSnapshot source2 = stats.sourceSnapshots().get(2);

        assertEquals(2, source1.generated());
        assertEquals(2, source1.rejected());
        assertEquals(0, source1.completed());
        assertEquals(1.0, source1.rejectionProbability());

        assertNull(source1.meanWaiting());
        assertNull(source1.meanService());
        assertNull(source1.meanStay());
        assertNull(source1.varianceWaiting());
        assertNull(source1.varianceService());

        assertEquals(0, source2.generated());
        assertEquals(0, source2.rejected());
        assertEquals(0.0, source2.rejectionProbability());
        assertNull(source2.meanService());

        assertNull(stats.requestSnapshot(1L).serviceTime());
        assertNull(stats.requestSnapshot(2L).serviceStartedAt());

        assertEquals(0.0, stats.processorSnapshots(4.0).get(1).busyTime());
    }

    @Test
    void shouldAccumulateBusyTimeForMultipleProcessors() {
        StatisticsCollector stats = collector();

        Request first = request(1, 1, 0.0);
        Request second = request(2, 2, 0.0);

        enqueue(stats, first);
        enqueue(stats, second);

        first.markProcessing();
        stats.recordServiceStart(1L, 1, 0.0);

        second.markProcessing();
        stats.recordServiceStart(2L, 2, 2.0);

        first.markCompleted();
        stats.recordCompleted(1L, 3.0);

        second.markCompleted();
        stats.recordCompleted(2L, 3.0);

        var processors = stats.processorSnapshots(3.0);

        assertEquals(3.0, processors.get(1).busyTime());
        assertEquals(1.0, processors.get(2).busyTime());

        assertEquals(1.0, processors.get(1).utilization());
        assertEquals(1.0 / 3.0, processors.get(2).utilization(), 1e-12);
    }

    @Test
    void shouldHandleZeroDurationAndZeroModelTime() {
        StatisticsCollector stats = collector();

        Request request = request(1, 1, 0.0);
        enqueue(stats, request);

        request.markProcessing();
        stats.recordServiceStart(1L, 1, 0.0);

        request.markCompleted();
        stats.recordCompleted(1L, 0.0);

        var requestStats = stats.requestSnapshot(1L);
        var sourceStats = stats.sourceSnapshots().get(1);
        var processorStats = stats.processorSnapshots(0.0).get(1);

        assertEquals(0.0, requestStats.waitingTime());
        assertEquals(0.0, requestStats.serviceTime());
        assertEquals(0.0, requestStats.stayTime());

        assertEquals(0.0, sourceStats.meanWaiting());
        assertEquals(0.0, sourceStats.varianceService());

        assertEquals(0.0, processorStats.busyTime());
        assertEquals(0.0, processorStats.utilization());
    }

    @Test
    void shouldRejectDuplicateTransitions() {
        StatisticsCollector stats = collector();

        Request request = request(1, 1, 0.0);
        stats.recordGenerated(request);

        assertThrows(
            IllegalStateException.class,
            () -> stats.recordGenerated(request)
        );

        stats.recordQueued(1L, 0.0);

        assertThrows(
            IllegalStateException.class,
            () -> stats.recordQueued(1L, 0.0)
        );

        stats.recordServiceStart(1L, 1, 1.0);
        stats.recordCompleted(1L, 3.0);

        assertThrows(
            IllegalStateException.class,
            () -> stats.recordCompleted(1L, 3.0)
        );

        assertThrows(
            IllegalStateException.class,
            () -> stats.recordRejected(1L, 4.0)
        );

        assertEquals(1, stats.sourceSnapshots().get(1).completed());
        assertEquals(0, stats.sourceSnapshots().get(1).rejected());
        assertEquals(2.0, stats.processorSnapshots(3.0).get(1).busyTime());
    }

    @Test
    void shouldRejectInvalidEventTimes() {
        StatisticsCollector stats = collector();

        Request request = request(1, 1, 5.0);
        stats.recordGenerated(request);

        assertThrows(
            IllegalArgumentException.class,
            () -> stats.recordQueued(1L, 4.0)
        );

        stats.recordQueued(1L, 5.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> stats.recordServiceStart(1L, 1, Double.NaN)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> stats.recordServiceStart(1L, 99, 6.0)
        );

        stats.recordServiceStart(1L, 1, 6.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> stats.recordCompleted(1L, 5.0)
        );

        assertEquals(RequestStatus.PROCESSING, stats.requestSnapshot(1L).status());
    }

    @Test
    void shouldReturnIndependentReadOnlySnapshots() {
        StatisticsCollector stats = collector();

        Request request = request(1, 1, 0.0);
        stats.recordGenerated(request);

        var before = stats.requestSnapshots();

        stats.recordQueued(1L, 1.0);

        assertEquals(RequestStatus.CREATED, before.get(1L).status());
        assertEquals(
            RequestStatus.QUEUED,
            stats.requestSnapshot(1L).status()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> before.clear()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> stats.sourceSnapshots().clear()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> stats.processorSnapshots(0.0).clear()
        );
    }
}
