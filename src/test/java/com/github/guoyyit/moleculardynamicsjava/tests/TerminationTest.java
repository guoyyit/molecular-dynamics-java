package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.engine.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.ParticleSpec;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminationTest {
    @Test
    void timeoutStopsRunBeforeAllStepsComplete(@TempDir Path tempDir) {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setSteps(10_000_000);
        config.setTimeoutMillis(200);
        config.setCheckpointInterval(100);

        SimulationResult result = new SimulationEngine().run(config, tempDir.resolve("timeout"),
                new AtomicBoolean(false));
        assertEquals(TerminationReason.TIMEOUT, result.getTerminationReason());
        assertTrue(result.getSteps() < 10_000_000);
        assertTrue(result.getMessage().contains("超时"));
    }

    @Test
    void numericalDivergenceIsDetected(@TempDir Path tempDir) {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setTimeStep(0.2);
        config.setSteps(5000);
        config.setMaxTemperature(20.0);

        SimulationResult result = new SimulationEngine().run(config, tempDir.resolve("diverged"),
                new AtomicBoolean(false));
        assertEquals(TerminationReason.DIVERGED, result.getTerminationReason(),
                "大步长应触发数值发散受控终止");
        assertTrue(result.getSteps() < 5000);
    }

    @Test
    void terminationLeavesRecoverableCheckpoint(@TempDir Path tempDir) {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setSteps(10_000_000);
        config.setTimeoutMillis(150);
        config.setCheckpointInterval(100);

        Path directory = tempDir.resolve("recoverable");
        SimulationResult result = new SimulationEngine().run(config, directory,
                new AtomicBoolean(false));
        assertEquals(TerminationReason.TIMEOUT, result.getTerminationReason());

        SimulationResult resumed = new SimulationEngine().resume(directory, 200,
                new AtomicBoolean(false));
        assertEquals(TerminationReason.COMPLETED, resumed.getTerminationReason(),
                "终止后最近提交的检查点必须可恢复");
        assertTrue(resumed.getSamples().size() >= 2);
    }
}
