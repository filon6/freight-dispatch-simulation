package org.example.smo.engine;

import org.example.smo.buffer.Buffer;
import org.example.smo.event.EventType;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.model.Source;
import org.example.smo.processor.Processor;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.processor.ServiceTimeGenerator;
import org.example.smo.stats.StatisticsCollector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private Source source(int id, double lambda) {
        return new Source(id, "Source " + id, lambda, () -> 0.5);
    }

    private SimulationEngine engine(
        List<Source> sources,
        int targetRequests,
        int bufferCapacity,
        int processorCount,
        double serviceTime
    ) {
        List<Processor> processors = new ArrayList<>();

        for (int id = 1; id <= processorCount; id++) {
            processors.add(new Processor(
                id,
                new ServiceTimeGenerator(serviceTime, serviceTime, () -> 0.5)
            ));
        }

        return new SimulationEngine(
            sources,
            targetRequests,
            Map.of(EventType.SERVICE_COMPLETED, 0, EventType.ARRIVAL, 1),
            new Buffer(bufferCapacity),
            new ProcessorPool(processors)
        );
    }

    private long count(SimulationEngine engine, RequestStatus status) {
        return engine.getRequests().stream()
            .filter(request -> request.getStatus() == status)
            .count();
    }

    @Test
    void shouldProcessEveryEventAndKeepTimeMonotonic() {
        SimulationEngine engine = engine(List.of(source(1, 1.0)), 3, 2, 1, 2.0);
        engine.initialize();

        double previousTime = 0.0;
        int eventCount = 0;

        while (!engine.isFinished()) {
            Request affected = engine.step();

            assertNotNull(affected);
            assertNotNull(engine.getLastProcessedEvent());
            assertTrue(engine.getClock().getCurrentTime() >= previousTime);

            engine.assertInvariants();

            previousTime = engine.getClock().getCurrentTime();
            eventCount++;
        }

        assertEquals(6, eventCount);
        assertEquals(3L, count(engine, RequestStatus.COMPLETED));
        assertEquals(0L, count(engine, RequestStatus.REJECTED));
        assertEquals(0, engine.getBuffer().size());
        assertTrue(engine.getCalendar().isEmpty());
        assertNull(engine.step());
    }

    @Test
    void shouldEvictOldestRequestsAndDrainRemainingOnes() {
        SimulationEngine engine = engine(List.of(source(1, 1.0)), 4, 1, 1, 10.0);
        engine.initialize();
        engine.runToCompletion();

        assertEquals(4L, engine.getGeneratedTotal());
        assertEquals(2L, count(engine, RequestStatus.COMPLETED));
        assertEquals(2L, count(engine, RequestStatus.REJECTED));

        assertEquals(RequestStatus.REJECTED, engine.getRequest(2).getStatus());
        assertEquals(RequestStatus.REJECTED, engine.getRequest(3).getStatus());
        assertEquals(RequestStatus.COMPLETED, engine.getRequest(4).getStatus());

        assertEquals(0, engine.getBuffer().size());
        assertTrue(engine.getProcessors().hasFreeProcessor());

        engine.assertInvariants();
    }

    @Test
    void shouldKeepOtherSourceWaitingUntilActiveBatchFinishes() {
        SimulationEngine engine = engine(
            List.of(source(1, 1.0), source(2, 1.0)),
            2,
            2,
            2,
            5.0
        );

        engine.initialize();

        engine.step();
        engine.step();

        assertEquals(1, engine.getSelection().activeBatchSnapshot().sourceId());
        assertEquals(RequestStatus.PROCESSING, engine.getRequest(1).getStatus());
        assertEquals(RequestStatus.QUEUED, engine.getRequest(2).getStatus());

        // Второй прибор свободен, но новый пакет брать нельзя.
        assertTrue(engine.getProcessors().hasFreeProcessor());

        engine.step();

        assertEquals(RequestStatus.COMPLETED, engine.getRequest(1).getStatus());
        assertEquals(RequestStatus.PROCESSING, engine.getRequest(2).getStatus());

        engine.runToCompletion();

        assertEquals(2L, count(engine, RequestStatus.COMPLETED));
    }

    @Test
    void shouldCancelUnusedArrivalsWithoutAdvancingTime() {
        SimulationEngine engine = engine(
            List.of(source(1, 0.01), source(2, 10.0)),
            1,
            1,
            1,
            2.0
        );

        engine.initialize();
        engine.runToCompletion();

        assertEquals(1L, engine.getGeneratedTotal());
        assertTrue(engine.getCalendar().isEmpty());
        assertTrue(engine.getClock().getCurrentTime() < 10.0);
    }

    @Test
    void runAndStepShouldProduceIdenticalResults() {
        SimulationEngine automatic = engine(
            List.of(source(1, 1.0), source(2, 2.0)),
            6,
            3,
            2,
            4.0
        );

        SimulationEngine manual = engine(
            List.of(source(1, 1.0), source(2, 2.0)),
            6,
            3,
            2,
            4.0
        );

        automatic.initialize();
        manual.initialize();

        automatic.runToCompletion();

        while (!manual.isFinished()) {
            manual.step();
        }

        assertEquals(
            automatic.getClock().getCurrentTime(),
            manual.getClock().getCurrentTime()
        );

        assertEquals(
            automatic.getGeneratedTotal(),
            manual.getGeneratedTotal()
        );

        for (int id = 1; id <= 6; id++) {
            assertEquals(
                automatic.getRequest(id).getStatus(),
                manual.getRequest(id).getStatus()
            );

            assertEquals(
                automatic.getRequest(id).getCreatedAt(),
                manual.getRequest(id).getCreatedAt()
            );
        }
    }

    @Test
    void shouldDetectDeadlockInsteadOfLoopingForever() {
        SimulationEngine engine = engine(List.of(source(1, 1.0)), 1, 1, 1, 2.0);
        engine.initialize();

        // Искусственно удаляем все события, хотя генерация не завершена.
        engine.getCalendar().cancelPendingArrivals();

        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void shouldRejectDuplicateCompletionEvent() {
        SimulationEngine engine = engine(List.of(source(1, 1.0)), 1, 1, 1, 2.0);
        engine.initialize();
        engine.step();

        SimulationEvent completion = engine.getCalendar().peek();

        engine.getCalendar().schedule(SimulationEvent.serviceCompleted(
            completion.time(),
            completion.requestId(),
            completion.sourceId(),
            completion.processorId()
        ));

        engine.step();

        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void shouldCompleteZeroDurationService() {
        SimulationEngine engine = engine(List.of(source(1, 1.0)), 2, 1, 1, 0.0);
        engine.initialize();
        engine.runToCompletion();

        assertEquals(2L, count(engine, RequestStatus.COMPLETED));
        assertEquals(0L, count(engine, RequestStatus.REJECTED));
        assertTrue(engine.isFinished());
    }

    @Test
    void shouldKeepStatisticsConsistentWithEngine() {
        SimulationEngine engine = engine(
            List.of(source(1, 1.0)),
            4,
            1,
            1,
            10.0
        );

        engine.initialize();
        engine.runToCompletion();

        var statistics = engine.getStatistics();
        var source = statistics.sourceSnapshots().get(1);

        assertEquals(4L, source.generated());
        assertEquals(2L, source.rejected());
        assertEquals(2L, source.completed());
        assertEquals(0.5, source.rejectionProbability());

        for (Request request : engine.getRequests()) {
            var recorded = statistics.requestSnapshot(request.getId());

            assertNotNull(recorded);
            assertEquals(request.getStatus(), recorded.status());
            assertEquals(request.getCreatedAt(), recorded.arrivalAt());
        }

        assertNull(statistics.requestSnapshot(2L).serviceTime());
        assertNull(statistics.requestSnapshot(3L).serviceTime());

        double totalTime = engine.getClock().getCurrentTime();

        double recordedBusyTime = statistics.processorSnapshots(totalTime)
            .get(1)
            .busyTime();

        double actualBusyTime = engine.getProcessors()
            .snapshot()
            .processors()
            .get(0)
            .busyTime();

        assertEquals(actualBusyTime, recordedBusyTime, 1e-12);

        engine.assertInvariants();
    }
}
