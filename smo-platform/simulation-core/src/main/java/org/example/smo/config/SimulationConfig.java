package org.example.smo.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SimulationConfig(
    List<SourceConfig> sources,
    int bufferCapacity,
    int processorCount,
    double serviceTimeMin,
    double serviceTimeMax,
    long targetRequests,
    long randomSeed
) {
    public SimulationConfig {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required");
        }

        sources = List.copyOf(sources);

        Set<Integer> sourceIds = new HashSet<>();

        for (SourceConfig source : sources) {
            if (!sourceIds.add(source.id())) {
                throw new IllegalArgumentException("Duplicate source ID: " + source.id());
            }
        }

        if (bufferCapacity < 1) {
            throw new IllegalArgumentException("Buffer capacity must be positive");
        }

        if (processorCount < 1) {
            throw new IllegalArgumentException("Processor count must be positive");
        }

        if (!Double.isFinite(serviceTimeMin) ||
            !Double.isFinite(serviceTimeMax) ||
            serviceTimeMin < 0 ||
            serviceTimeMin > serviceTimeMax) {
            throw new IllegalArgumentException("Invalid service time bounds");
        }

        if (targetRequests < 1) {
            throw new IllegalArgumentException("Target requests must be positive");
        }
    }
}
