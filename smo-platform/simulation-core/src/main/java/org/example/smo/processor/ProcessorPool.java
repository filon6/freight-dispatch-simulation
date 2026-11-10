package org.example.smo.processor;

import org.example.smo.event.EventType;
import org.example.smo.event.SimulationEvent;
import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProcessorPool {
    private final List<Processor> processors;
    private int nextIndex;

    public ProcessorPool(List<Processor> processors) {
        Objects.requireNonNull(processors);

        if (processors.isEmpty()) {
            throw new IllegalArgumentException("At least one processor is required");
        }

        List<Processor> sorted = new ArrayList<>(processors);

        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Null processor");
        }

        sorted.sort(Comparator.comparingInt(Processor::getId));

        Set<Integer> ids = new HashSet<>();

        for (Processor processor : sorted) {
            if (!ids.add(processor.getId())) {
                throw new IllegalArgumentException("Duplicate processor ID");
            }
        }

        this.processors = List.copyOf(sorted);
    }

    public boolean hasFreeProcessor() {
        return processors.stream().anyMatch(
            processor -> !processor.isBusy()
        );
    }

    public SimulationEvent assign(Request request, double time) {
        Objects.requireNonNull(request);

        if (!Double.isFinite(time) || time < 0 || time < request.getCreatedAt()) {
            throw new IllegalArgumentException("Invalid assignment time");
        }

        if (request.getStatus() != RequestStatus.QUEUED) {
            throw new IllegalArgumentException("Only queued requests can be assigned");
        }

        for (int offset = 0; offset < processors.size(); offset++) {
            int index = (nextIndex + offset) % processors.size();
            Processor processor = processors.get(index);

            if (!processor.isBusy()) {
                double endTime = processor.start(request.getId(), time);

                request.markProcessing();

                nextIndex = (index + 1) % processors.size();

                return SimulationEvent.serviceCompleted(
                    endTime,
                    request.getId(),
                    request.getSourceId(),
                    processor.getId()
                );
            }
        }

        return null;
    }

    public void complete(SimulationEvent event) {
        Objects.requireNonNull(event);

        if (event.type() != EventType.SERVICE_COMPLETED) {
            throw new IllegalArgumentException("Not a completion event");
        }

        for (Processor processor : processors) {
            if (processor.getId() == event.processorId()) {
                processor.complete(
                    event.requestId(),
                    event.time()
                );
                return;
            }
        }

        throw new IllegalArgumentException("Unknown processor ID");
    }

    public Snapshot snapshot() {
        List<Processor.Snapshot> states = processors.stream()
            .map(Processor::snapshot)
            .toList();

        return new Snapshot(
            processors.get(nextIndex).getId(),
            states
        );
    }

    public record Snapshot(
        int nextProcessorId,
        List<Processor.Snapshot> processors
    ) {}
}
