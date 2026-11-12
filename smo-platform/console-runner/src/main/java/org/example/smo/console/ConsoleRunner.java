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
        initialize();

        out.println("Модель инициализирована");
        out.println("Команды: Enter — следующий шаг, a — автоматический режим, q — выход");

        ConsolePrinter.printState(engine, stepCount, out);

        while (true) {
            out.print("> ");
            out.flush();

            String line = input.readLine();

            if (line == null) {
                return;
            }

            String command = line.trim().toLowerCase(Locale.ROOT);

            switch (command) {
                case "" -> nextStep();

                case "a" -> {
                    runToEnd();
                    return;
                }

                case "q" -> {
                    return;
                }

                default -> out.println("Нажми Enter для следующего шага, 'a' — до конца, 'q' — выход.");
            }
        }
    }

    private void nextStep() {
        if (engine.isFinished()) {
            out.println("Моделирование уже завершено.");
            ConsolePrinter.printFinalTables(engine, out);
            return;
        }

        engine.step();
        stepCount++;

        ConsolePrinter.printState(engine, stepCount, out);

        if (engine.isFinished()) {
            out.println("\nМоделирование завершено.");
            ConsolePrinter.printFinalTables(engine, out);
        }
    }

    private void runToEnd() {
        while (!engine.isFinished()) {
            engine.step();
            stepCount++;
        }

        out.println("\nМоделирование завершено.");
        ConsolePrinter.printFinalTables(engine, out);
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
            Map.of(
                EventType.SERVICE_COMPLETED, 0,
                EventType.ARRIVAL, 1
            ),
            new Buffer(config.bufferCapacity()),
            new ProcessorPool(processors)
        );

        engine.initialize();
        stepCount = 0;
    }
}
