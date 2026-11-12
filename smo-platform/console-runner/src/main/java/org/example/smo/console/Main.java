package org.example.smo.console;

import org.example.smo.config.SimulationConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Main {
    public static void main(String[] args) {
        String configPath = "../configs/example.properties";
        boolean automatic = false;
        boolean pathProvided = false;

        for (String arg : args) {
            if (arg.equals("--auto")) {
                automatic = true;
            } else if (arg.equals("--step")) {
                automatic = false;
            } else if (arg.startsWith("--") || pathProvided) {
                System.err.println("Unknown argument: " + arg);
                System.exit(1);
                return;
            } else {
                configPath = arg;
                pathProvided = true;
            }
        }

        try {
            SimulationConfig config = new SimulationConfigReader().read(configPath);

            ConsoleRunner runner = new ConsoleRunner(
                config,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out
            );

            if (automatic) {
                runner.runAutomatic();
            } else {
                runner.runInteractive();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("Simulation error: " + e.getMessage());
            System.exit(1);
        }
    }
}
