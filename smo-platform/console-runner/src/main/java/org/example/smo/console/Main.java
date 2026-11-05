package org.example.smo.console;

import org.example.smo.config.SimulationConfig;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        SimulationConfigReader configReader = new SimulationConfigReader();

        String configPath = args.length > 0
            ? args[0]
            : "../configs/example.properties";

        try {
            SimulationConfig config = configReader.read(configPath);

            System.out.println("SMO Simulation");
            System.out.println("Sources: " + config.sources().size());
            System.out.println("Processors: " + config.processorCount());
            System.out.println("Buffer capacity: " + config.bufferCapacity());
            System.out.println("Random seed: " + config.randomSeed());

        } catch (IOException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
        }
    }
}
