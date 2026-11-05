package org.example.smo.console;

import org.example.smo.config.SimulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigReaderTest {
    @TempDir
    Path tempDir;

    private Path createConfig(String content) throws IOException {
        Path path = tempDir.resolve("test.properties");
        Files.writeString(path, content);
        return path;
    }

    private String validConfig() {
        return """
                simulation.sources=2
                simulation.source.1.lambda=0.5
                simulation.source.2.lambda=0.8
                simulation.buffer-capacity=8
                simulation.processors=2
                simulation.service-min=1.0
                simulation.service-max=5.0
                simulation.target-requests=1000
                simulation.seed=42
                """;
    }

    @Test
    void shouldReadValidConfiguration() throws IOException {
        Path path = createConfig(validConfig());

        SimulationConfigReader reader = new SimulationConfigReader();
        SimulationConfig config = reader.read(path.toString());

        assertEquals(2, config.sources().size());
        assertEquals(8, config.bufferCapacity());
        assertEquals(2, config.processorCount());
        assertEquals(42, config.randomSeed());
    }

    @Test
    void shouldRejectInvalidLambda() throws IOException {
        String content = validConfig().replace(
            "simulation.source.1.lambda=0.5",
            "simulation.source.1.lambda=-0.5"
        );

        Path path = createConfig(content);
        SimulationConfigReader reader = new SimulationConfigReader();

        assertThrows(IOException.class, () -> reader.read(path.toString()));
    }

    @Test
    void shouldRejectInvalidProcessorCount() throws IOException {
        String content = validConfig().replace(
            "simulation.processors=2",
            "simulation.processors=0"
        );

        Path path = createConfig(content);
        SimulationConfigReader reader = new SimulationConfigReader();

        assertThrows(IOException.class, () -> reader.read(path.toString()));
    }
}
