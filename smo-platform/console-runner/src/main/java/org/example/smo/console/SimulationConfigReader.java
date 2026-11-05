package org.example.smo.console;

import org.example.smo.config.SimulationConfig;
import org.example.smo.config.SourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SimulationConfigReader {

    public SimulationConfig read(String configPath) throws IOException {
        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(Path.of(configPath))) {
            properties.load(input);
        }

        try {
            int sourceCount = Integer.parseInt(required(properties, "simulation.sources"));

            List<SourceConfig> sources = new ArrayList<>();

            for (int id = 1; id <= sourceCount; id++) {
                double lambda = Double.parseDouble(required(properties, "simulation.source." + id + ".lambda"));
                sources.add(new SourceConfig(id, lambda));
            }

            int bufferCapacity = Integer.parseInt(required(properties, "simulation.buffer-capacity"));
            int processorCount = Integer.parseInt(required(properties, "simulation.processors"));

            double serviceMin = Double.parseDouble(required(properties, "simulation.service-min"));
            double serviceMax = Double.parseDouble(required(properties, "simulation.service-max"));

            long targetRequests = Long.parseLong(required(properties, "simulation.target-requests"));
            long randomSeed = Long.parseLong(required(properties, "simulation.seed"));

            return new SimulationConfig(
                sources,
                bufferCapacity,
                processorCount,
                serviceMin,
                serviceMax,
                targetRequests,
                randomSeed
            );

        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid simulation configuration: " + e.getMessage(), e);
        }
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }

        return value.trim();
    }
}
