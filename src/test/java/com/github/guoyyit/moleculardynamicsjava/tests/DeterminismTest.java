package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.engine.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.Sample;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterminismTest {
    @Test
    void sameSeedProducesBitwiseIdenticalTrajectories(@TempDir Path tempDir) {
        SimulationConfig firstConfig = ExampleSystems.all().get("argon-fcc-nvt");
        SimulationConfig secondConfig = ExampleSystems.all().get("argon-fcc-nvt");

        SimulationResult first = new SimulationEngine().run(firstConfig,
                tempDir.resolve("run-a"), new AtomicBoolean(false));
        SimulationResult second = new SimulationEngine().run(secondConfig,
                tempDir.resolve("run-b"), new AtomicBoolean(false));

        assertEquals(first.getSamples().size(), second.getSamples().size());
        for (int i = 0; i < first.getSamples().size(); i++) {
            Sample a = first.getSamples().get(i);
            Sample b = second.getSamples().get(i);
            assertEquals(a.step(), b.step());
            assertEquals(a.time(), b.time(), 0.0);
            assertEquals(Double.doubleToLongBits(a.temperature()),
                    Double.doubleToLongBits(b.temperature()));
            assertEquals(Double.doubleToLongBits(a.totalEnergy()),
                    Double.doubleToLongBits(b.totalEnergy()));
            assertEquals(Double.doubleToLongBits(a.potentialEnergy()),
                    Double.doubleToLongBits(b.potentialEnergy()));
        }
        for (int i = 0; i < first.getRdfValues().length; i++) {
            assertEquals(Double.doubleToLongBits(first.getRdfValues()[i]),
                    Double.doubleToLongBits(second.getRdfValues()[i]));
        }
    }

    @Test
    void differentSeedsChangeTrajectory(@TempDir Path tempDir) {
        SimulationConfig first = ExampleSystems.all().get("argon-fcc-nvt");
        SimulationConfig second = ExampleSystems.all().get("argon-fcc-nvt");
        second.setSeed(9999L);

        SimulationResult firstResult = new SimulationEngine().run(first,
                tempDir.resolve("seed-a"), new AtomicBoolean(false));
        SimulationResult secondResult = new SimulationEngine().run(second,
                tempDir.resolve("seed-b"), new AtomicBoolean(false));

        boolean identical = true;
        for (int i = 1; i < firstResult.getSamples().size(); i++) {
            if (Double.doubleToLongBits(firstResult.getSamples().get(i).totalEnergy())
                    != Double.doubleToLongBits(secondResult.getSamples().get(i).totalEnergy())) {
                identical = false;
                break;
            }
        }
        assertEquals(false, identical, "不同种子必须产生不同轨迹");
    }
}
