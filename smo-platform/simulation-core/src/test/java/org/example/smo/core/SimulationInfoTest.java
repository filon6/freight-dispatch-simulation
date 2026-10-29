package org.example.smo.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationInfoTest {

    @Test
    void shouldReturnSimulationName() {
        SimulationInfo simulationInfo = new SimulationInfo();
        assertEquals("SMO Simulation Engine", simulationInfo.getName());
    }
}