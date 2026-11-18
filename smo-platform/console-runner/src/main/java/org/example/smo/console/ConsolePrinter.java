package org.example.smo.console;

import org.example.smo.batch.ProcessingBatch;
import org.example.smo.config.SimulationConfig;
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
            "Создано: %d | Буфер: %d | В работе: %d | Готово: %d | Отказы: %d | Pотк: %.1f%%%n",
            engine.getGeneratedTotal(),
            queued,
            processing,
            completed,
            rejected,
            engine.getGeneratedTotal() == 0 ? 0.0 : 100.0 * rejected / engine.getGeneratedTotal()
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

    /**
     * Итоги одного эксперимента: сводка и две таблицы без графиков.
     * Все числовые показатели берутся из накопленной статистики модели.
     */
    public static void printFinalTables(
        SimulationEngine engine,
        SimulationConfig config,
        int stepCount,
        PrintStream out
    ) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(config);
        Objects.requireNonNull(out);

        var stats = engine.getStatistics();
        var sourceStats = stats.sourceSnapshots();
        var processorStats = stats.processorSnapshots(engine.getClock().getCurrentTime());

        long generated = engine.getGeneratedTotal();
        long rejected = sourceStats.values().stream().mapToLong(StatisticsCollector.SourceSnapshot::rejected).sum();
        long completed = sourceStats.values().stream().mapToLong(StatisticsCollector.SourceSnapshot::completed).sum();
        double totalTime = engine.getClock().getCurrentTime();
        double rejectionPercent = generated == 0 ? 0.0 : 100.0 * rejected / generated;

        out.println();
        out.println("===============================================================================================");
        out.println("МОДЕЛИРОВАНИЕ ЗАВЕРШЕНО");
        out.printf(Locale.ROOT, "Время моделирования: %.3f | шагов: %d%n", totalTime, stepCount);
        out.printf(
            Locale.ROOT,
            "Заявок: создано %d; обслужено %d; отказано %d; Pотк = %.1f%%%n",
            generated, completed, rejected, rejectionPercent
        );
        out.println("-----------------------------------------------------------------------------------------------");

        out.println("ИСТОЧНИК | Nген | Nотк | Nобсл | Pотк,% |    Tож |  Tобсл |  Tсист |    Dож |  Dобсл");
        for (var source : sourceStats.values()) {
            out.printf(
                Locale.ROOT,
                "И%-7d | %4d | %4d | %5d | %6.1f | %6s | %6s | %6s | %6s | %6s%n",
                source.sourceId(),
                source.generated(),
                source.rejected(),
                source.completed(),
                source.rejectionProbability() * 100.0,
                metric(source.meanWaiting()),
                metric(source.meanService()),
                metric(source.meanStay()),
                metric(source.varianceWaiting()),
                metric(source.varianceService())
            );
        }

        out.println();
        out.println("ПРИБОР | Выполнено | Время занятости |   Кисп");
        var requestStats = stats.requestSnapshots();
        for (var processor : processorStats.values()) {
            long handled = requestStats.values().stream()
                .filter(request -> request.status() == RequestStatus.COMPLETED)
                .filter(request -> Objects.equals(request.processorId(), processor.processorId()))
                .count();

            out.printf(
                Locale.ROOT,
                "П%-5d | %9d | %15.3f | %6.2f%%%n",
                processor.processorId(),
                handled,
                processor.busyTime(),
                processor.utilization() * 100.0
            );
        }

        double meanServiceTime = (config.serviceTimeMin() + config.serviceTimeMax()) / 2.0;
        double totalLambda = config.sources().stream().mapToDouble(source -> source.lambda()).sum();
        if (meanServiceTime > 0.0) {
            double offeredLoad = totalLambda * meanServiceTime / config.processorCount();
            out.printf(
                Locale.ROOT,
                "%nОценка входной нагрузки rho = Σlambda/Σmu = %.3f (не равна фактическому Кисп)%n",
                offeredLoad
            );
        } else {
            out.println("\nОценка входной нагрузки rho не определена при нулевом среднем времени обслуживания.");
        }
        out.println("Tож, Tобсл, Tсист и дисперсии: по успешно завершённым заявкам каждого источника.");
        out.println("Дисперсии генеральные (деление на n); при отсутствии наблюдений показано «—».");
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
