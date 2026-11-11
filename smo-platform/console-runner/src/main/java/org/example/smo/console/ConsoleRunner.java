package org.example.smo.console;

import org.example.smo.buffer.Buffer;
import org.example.smo.config.SimulationConfig;
import org.example.smo.config.SourceConfig;
import org.example.smo.engine.SimulationEngine;
import org.example.smo.event.EventType;
import org.example.smo.model.Source;
import org.example.smo.processor.Processor;
import org.example.smo.processor.ProcessorPool;
import org.example.smo.processor.ServiceTimeGenerator;
import org.example.smo.random.RandomStreams;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConsoleRunner {
    private final SimulationConfig config;
    private final BufferedReader input;
    private final PrintStream out;

    private SimulationEngine engine;
    private int stepCount;

    public ConsoleRunner(
        SimulationConfig config,
        BufferedReader input,
        PrintStream out
    ) {
        this.config = Objects.requireNonNull(config);
        this.input = Objects.requireNonNull(input);
        this.out = Objects.requireNonNull(out);
    }

    public void runAutomatic() {
        initialize();
        engine.runToCompletion();
        ConsolePrinter.printFinalTables(engine, out);
    }

    public void runInteractive() throws IOException {
        out.println("Команды: init, step, state, run, help, exit");

        while (true) {
            out.print("> ");
            out.flush();

            String line = input.readLine();

            if (line == null) {
                return;
            }

            switch (line.trim().toLowerCase(Locale.ROOT)) {
                case "init" -> {
                    initialize();
                    out.println("Модель инициализирована.");
                    ConsolePrinter.printState(engine, 0, out);
                }
                case "step" -> {
                    if (!ensureInitialized()) {
                        break;
                    }

                    if (engine.isFinished()) {
                        out.println("Моделирование уже завершено.");
                        break;
                    }

                    engine.step();
                    stepCount++;

                    ConsolePrinter.printState(engine, stepCount, out);
                }
                case "state" -> {
                    if (ensureInitialized()) {
                        ConsolePrinter.printState(engine, stepCount, out);
                    }
                }
                case "run" -> {
                    if (ensureInitialized()) {
                        engine.runToCompletion();
                        ConsolePrinter.printFinalTables(engine, out);
                    }
                }
                case "help" -> out.println(
                    "init — новый запуск; step — одно событие; "
                        + "state — состояние; run — до конца и две таблицы; "
                        + "exit — выход"
                );
                case "exit", "quit" -> {
                    return;
                }
                case "" -> {
                }
                default -> out.println("Неизвестная команда. Используй help.");
            }
        }
    }

    private boolean ensureInitialized() {
        if (engine != null) {
            return true;
        }

        out.println("Сначала введи init.");
        return false;
    }

    private void initialize() {
        RandomStreams random = new RandomStreams(config.randomSeed());

        List<Source> sources = new ArrayList<>();
        List<Processor> processors = new ArrayList<>();

        for (SourceConfig source : config.sources()) {
            sources.add(new Source(
                source.id(),
                "Source " + source.id(),
                source.lambda(),
                random.forSource(source.id())
            ));
        }

        for (int id = 1; id <= config.processorCount(); id++) {
            processors.add(new Processor(
                id,
                new ServiceTimeGenerator(
                    config.serviceTimeMin(),
                    config.serviceTimeMax(),
                    random.forProcessor(id)
                )
            ));
        }

        engine = new SimulationEngine(
            sources,
            config.targetRequests(),
            Map.of(EventType.SERVICE_COMPLETED, 0, EventType.ARRIVAL, 1),
            new Buffer(config.bufferCapacity()),
            new ProcessorPool(processors)
        );

        engine.initialize();
        stepCount = 0;
    }
}
