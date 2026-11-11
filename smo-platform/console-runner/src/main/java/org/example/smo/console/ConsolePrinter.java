package org.example.smo.console;

import org.example.smo.batch.ProcessingBatch;
import org.example.smo.engine.SimulationEngine;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;
import org.example.smo.processor.Processor;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.stats.StatisticsCollector;
import org.example.smo.buffer.PlacementResult;
import org.example.smo.model.Request;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConsolePrinter {
    private ConsolePrinter() {}

    public static void printState(SimulationEngine engine, int stepCount, PrintStream out) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(out);

        SimulationEvent event = engine.getLastProcessedEvent();

        String eventName = event == null ? "INITIAL" : event.type().name();

        String eventDetails = event == null
            ? ""
            : " | source=" + event.sourceId()
            + (event.requestId() == null ? "" : " | request=" + event.requestId());

        out.printf(
            Locale.ROOT,
            "%nШАГ %d | t=%.3f | %s%s%n",
            stepCount,
            engine.getClock().getCurrentTime(),
            eventName,
            eventDetails
        );
        printEventDetails(engine, out);

        out.println("ИСТОЧНИКИ");
        out.println("ID | Создано | В буфере | На приборах | Завершено | Отказов");

        Map<Integer, StatisticsCollector.SourceSnapshot> sources =
            engine.getStatistics().sourceSnapshots();

        List<Request> requests = engine.getRequests();

        for (var source : sources.values()) {
            long queued = requests.stream()
                .filter(r -> r.getSourceId() == source.sourceId())
                .filter(r -> r.getStatus() == RequestStatus.QUEUED)
                .count();

            long processing = requests.stream()
                .filter(r -> r.getSourceId() == source.sourceId())
                .filter(r -> r.getStatus() == RequestStatus.PROCESSING)
                .count();

            out.printf(
                Locale.ROOT,
                "%2d | %7d | %8d | %10d | %9d | %7d%n",
                source.sourceId(),
                source.generated(),
                queued,
                processing,
                source.completed(),
                source.rejected()
            );
        }

        out.println("БУФЕР");

        List<Request> slots = engine.getBuffer().snapshot();

        for (int index = 0; index < slots.size(); index++) {
            Request request = slots.get(index);

            if (request == null) {
                out.printf(Locale.ROOT, "[%d] свободно%n", index);
            } else {
                out.printf(
                    Locale.ROOT,
                    "[%d] заявка %d, источник %d%n",
                    index,
                    request.getId(),
                    request.getSourceId()
                );
            }
        }

        out.println("ПРИБОРЫ");

        ProcessorPool.Snapshot pool = engine.getProcessors().snapshot();

        for (Processor.Snapshot processor : pool.processors()) {
            if (processor.currentRequestId() == null) {
                out.printf(
                    Locale.ROOT,
                    "П%d: свободен, busyTime=%.3f%n",
                    processor.id(),
                    processor.busyTime()
                );
            } else {
                out.printf(
                    Locale.ROOT,
                    "П%d: заявка %d, начало=%.3f, конец=%.3f, busyTime=%.3f%n",
                    processor.id(),
                    processor.currentRequestId(),
                    processor.busySince(),
                    processor.completionTime(),
                    processor.busyTime()
                );
            }
        }

        out.println("Следующий поиск прибора: П" + pool.nextProcessorId());

        ProcessingBatch.Snapshot batch = engine.getSelection().activeBatchSnapshot();

        if (batch == null) {
            out.println("АКТИВНЫЙ ПАКЕТ: нет");
        } else {
            out.printf(
                "АКТИВНЫЙ ПАКЕТ: источник %d | pending=%s | running=%s | completed=%s | evicted=%s%n",
                batch.sourceId(),
                batch.pending(),
                batch.running(),
                batch.completed(),
                batch.evicted()
            );
        }
    }

    public static void printFinalTables(SimulationEngine engine, PrintStream out) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(out);

        out.println("ИТОГИ ПО ИСТОЧНИКАМ");
        out.println("ID | Создано | Отказов | Завершено | Pотк | Tпреб | Tож | Tобсл | Dож | Dобсл");

        for (var source : engine.getStatistics().sourceSnapshots().values()) {
            out.printf(
                Locale.ROOT,
                "%d | %d | %d | %d | %s | %s | %s | %s | %s | %s%n",
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

        out.println("ИТОГИ ПО ПРИБОРАМ");
        out.println("ID | Время занятости | Коэффициент использования");

        for (var processor : engine.getStatistics()
            .processorSnapshots(engine.getClock().getCurrentTime())
            .values()) {

            out.printf(
                Locale.ROOT,
                "%d | %s | %s%n",
                processor.processorId(),
                metric(processor.busyTime()),
                metric(processor.utilization())
            );
        }
    }

    private static String metric(Double value) {
        return value == null
            ? "—"
            : String.format(Locale.ROOT, "%.3f", value);
    }

    private static void printEventDetails(
        SimulationEngine engine,
        PrintStream out
    ) {
        SimulationEvent event = engine.getLastProcessedEvent();

        if (event == null) {
            return;
        }

        Request request = engine.getLastAffectedRequest();

        if (request == null) {
            throw new IllegalStateException("Last affected request is missing");
        }

        switch (event.type()) {
            case ARRIVAL -> {
                out.printf(
                    "СОБЫТИЕ: поступила заявка №%d от источника И%d%n",
                    request.getId(),
                    request.getSourceId()
                );

                PlacementResult result = engine.getLastPlacementResult();

                if (result == null) {
                    throw new IllegalStateException("Placement result is missing");
                }

                out.printf(
                    "РАЗМЕЩЕНИЕ: ячейка %d%n",
                    result.slotIndex()
                );

                Request evicted = result.evictedRequest();

                if (evicted != null) {
                    out.printf(
                        "ОТКАЗ: вытеснена заявка №%d источника И%d из ячейки %d%n",
                        evicted.getId(),
                        evicted.getSourceId(),
                        result.slotIndex()
                    );
                }
            }

            case SERVICE_COMPLETED -> out.printf(
                "СОБЫТИЕ: завершено обслуживание заявки №%d на приборе П%d%n",
                request.getId(),
                event.processorId()
            );
        }
    }
}
