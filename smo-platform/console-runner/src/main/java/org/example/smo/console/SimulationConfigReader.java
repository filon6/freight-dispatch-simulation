package org.example.smo.console;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SimulationConfigReader {
    public int readSourceCount(String configPath) throws IOException {
        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(Path.of(configPath))) {
            properties.load(input);
        }

        String value = properties.getProperty("simulation.sources");

        if (value == null) {
            throw new IOException("Missing property: simulation.sources");
        }

        try {
            int count = Integer.parseInt(value.trim());

            if (count <= 0) {
                throw new IOException("Source count must be positive");
            }
            return count;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid source count: " + value, e);
        }
    }
}
