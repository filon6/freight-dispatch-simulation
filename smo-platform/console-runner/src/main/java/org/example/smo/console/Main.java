package org.example.smo.console;

import org.example.smo.core.SimulationInfo;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        SimulationInfo simulationInfo = new SimulationInfo();
        SimulationConfigReader configReader = new SimulationConfigReader();

        String configPath = args.length > 0
            ? args[0]
            : "configs/example.properties";

        try {
            int sourceCount = configReader.readSourceCount(configPath);

            System.out.println(simulationInfo.getName());
            System.out.println("Sources: " + sourceCount);

        } catch (IOException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
        }
    }
}
