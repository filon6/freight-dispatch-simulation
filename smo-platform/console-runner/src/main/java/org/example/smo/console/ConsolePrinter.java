package org.example.smo.console;

import org.example.smo.batch.ProcessingBatch;
import org.example.smo.buffer.PlacementResult;
import org.example.smo.engine.SimulationEngine;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.processor.Processor;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.stats.StatisticsCollector;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConsolePrinter {
    private ConsolePrinter() {}

    public static void printState(SimulationEngine engine, int step, PrintStream out) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(out);

        SimulationEvent event = engine.getLastProcessedEvent();

        out.printf(
            Locale.ROOT,
            "%n========== ШАГ %d | t=%.3f ==========%n",
            step,
            engine.getClock().getCurrentTime()
        );

        printEvent(engine, event, out);

        List<Request> requests = engine.getRequests();

        long queued = count(requests, RequestStatus.QUEUED);
        long processing = count(requests, RequestStatus.PROCESSING);
        long completed = count(requests, RequestStatus.COMPLETED);
        long rejected = count(requests, RequestStatus.REJECTED);

        out.printf(
            Locale.ROOT,
            "Создано: %d | Буфер: %d | В работе: %d | Готово: %d | Отказы: %d%n",
            engine.getGeneratedTotal(),
            queued,
            processing,
            completed,
            rejected
        );

        out.println("\nИСТОЧНИКИ");
        out.println("ID | Создано | Буфер | Работа | Готово | Отказы");

        Map<Integer, StatisticsCollector.SourceSnapshot> sources =
            engine.getStatistics().sourceSnapshots();

        for (var source : sources.values()) {
            long sourceQueued = requests.stream()
                .filter(r -> r.getSourceId() == source.sourceId())
                .filter(r -> r.getStatus() == RequestStatus.QUEUED)
                .count();

            long sourceProcessing = requests.stream()
                .filter(r -> r.getSourceId() == source.sourceId())
                .filter(r -> r.getStatus() == RequestStatus.PROCESSING)
                .count();

            out.printf(
                Locale.ROOT,
                "%2d | %7d | %5d | %6d | %6d | %6d%n",
                source.sourceId(),
                source.generated(),
                sourceQueued,
                sourceProcessing,
                source.completed(),
                source.rejected()
            );
        }

        List<Request> slots = engine.getBuffer().snapshot();
        int occupied = (int) slots.stream()
            .filter(Objects::nonNull)
            .count();

        out.printf(
            "%nБУФЕР [%s] %d/%d%n",
            "█".repeat(occupied) + "░".repeat(slots.size() - occupied),
            occupied,
            slots.size()
        );

        for (int i = 0; i < slots.size(); i++) {
            Request request = slots.get(i);

            if (request == null) {
                out.printf("[%d] —%n", i);
            } else {
                out.printf(
                    "[%d] №%d (И%d)%n",
                    i,
                    request.getId(),
                    request.getSourceId()
                );
            }
        }

        out.println("\nПРИБОРЫ");

        ProcessorPool.Snapshot pool = engine.getProcessors().snapshot();

        for (Processor.Snapshot processor : pool.processors()) {
            if (processor.currentRequestId() == null) {
                out.printf("П%d | свободен%n", processor.id());
            } else {
                out.printf(
                    Locale.ROOT,
                    "П%d | занят №%d | до %.3f%n",
                    processor.id(),
                    processor.currentRequestId(),
                    processor.completionTime()
                );
            }
        }

        out.printf(
            "Следующий по кольцу: П%d%n",
            pool.nextProcessorId()
        );

        ProcessingBatch.Snapshot batch =
            engine.getSelection().activeBatchSnapshot();

        out.println("\nАКТИВНЫЙ ПАКЕТ");

        if (batch == null) {
            out.println("Нет");
        } else {
            out.printf("Источник: И%d%n", batch.sourceId());
            out.printf("Ожидают: %s%n", batch.pending());
            out.printf(
                "В работе (заявка=прибор): %s%n",
                batch.running()
            );
            out.printf(
                "Завершены: %s | Вытеснены: %s%n",
                batch.completed(),
                batch.evicted()
            );
        }
    }

    private static void printEvent(
        SimulationEngine engine,
        SimulationEvent event,
        PrintStream out
    ) {
        if (event == null) {
            out.println("Начальное состояние");
            return;
        }

        Request request = engine.getLastAffectedRequest();

        if (request == null) {
            throw new IllegalStateException(
                "Missing request for last event"
            );
        }

        switch (event.type()) {
            case ARRIVAL -> {
                out.printf(
                    "ARRIVAL: поступила заявка №%d от И%d%n",
                    request.getId(),
                    request.getSourceId()
                );

                PlacementResult placement =
                    engine.getLastPlacementResult();

                if (placement == null) {
                    throw new IllegalStateException(
                        "Missing placement result"
                    );
                }

                out.printf(
                    "Размещение: ячейка [%d]%n",
                    placement.slotIndex()
                );

                if (placement.evictedRequest() != null) {
                    out.printf(
                        "ОТКАЗ: вытеснена заявка №%d (И%d) из ячейки [%d]%n",
                        placement.evictedRequest().getId(),
                        placement.evictedRequest().getSourceId(),
                        placement.slotIndex()
                    );
                }
            }

            case SERVICE_COMPLETED -> out.printf(
                "SERVICE_COMPLETED: заявка №%d завершена на П%d%n",
                request.getId(),
                event.processorId()
            );
        }
    }

    public static void printFinalTables(
        SimulationEngine engine,
        PrintStream out
    ) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(out);

        out.println("ИТОГИ ПО ИСТОЧНИКАМ");
        out.println(
            "ID | Создано | Отказы | Готово | Pотк | Tпреб | Tож | Tобсл | Dож | Dобсл"
        );

        for (var source : engine.getStatistics().sourceSnapshots().values()) {
            out.printf(
                Locale.ROOT,
                "%2d | %7d | %6d | %6d | %5s | %5s | %5s | %6s | %5s | %6s%n",
                source.sourceId(),
                source.generated(),
                source.rejected(),
                source.completed(),
                metric(source.rejectionProbability()),
                metric(source.meanStay()),
                metric(source.meanWaiting()),
                metric(source.meanService()),
                metric(source.varianceWaiting()),
                metric(source.varianceService())
            );
        }

        out.println("\nИТОГИ ПО ПРИБОРАМ");
        out.println("ID | Занятость | Коэффициент использования");

        for (var processor : engine.getStatistics()
            .processorSnapshots(engine.getClock().getCurrentTime())
            .values()) {

            out.printf(
                Locale.ROOT,
                "%2d | %9s | %s%n",
                processor.processorId(),
                metric(processor.busyTime()),
                metric(processor.utilization())
            );
        }
    }

    private static long count(
        List<Request> requests,
        RequestStatus status
    ) {
        return requests.stream()
            .filter(request -> request.getStatus() == status)
            .count();
    }

    private static String metric(Double value) {
        return value == null
            ? "—"
            : String.format(Locale.ROOT, "%.3f", value);
    }
}
