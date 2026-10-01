package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermostatTest {
    @Test
    void nvtTemperatureConvergesToTargetWithBoundedFluctuation() throws Exception {
        Path directory = TestSupport.freshDirectory("md-nvt");
        try {
            SimulationConfig config = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);
            SimulationResult result = TestSupport.runFresh(config, directory);

            int half = result.samples.size() / 2;
            double mean = 0.0;
            int count = 0;
            for (int i = half; i < result.samples.size(); i++) {
                mean += result.samples.get(i).temperature;
                count++;
            }
            mean /= count;
            double deviation = StrictMath.abs(mean - config.targetTemperature)
                    / config.targetTemperature;
            assertTrue(deviation < 0.08,
                    "mean temperature " + mean + " deviates from target "
                            + config.targetTemperature + " by " + deviation);

            double variance = 0.0;
            for (int i = half; i < result.samples.size(); i++) {
                double d = result.samples.get(i).temperature - mean;
                variance += d * d;
            }
            variance /= count;
            double relativeRms = StrictMath.sqrt(variance) / config.targetTemperature;
            assertTrue(relativeRms < 0.20,
                    "temperature relative RMS fluctuation " + relativeRms + " too large");

            for (int i = half; i < result.samples.size(); i++) {
                TrajectorySample sample = result.samples.get(i);
                assertTrue(sample.temperature > 0.5 * config.targetTemperature
                                && sample.temperature < 1.5 * config.targetTemperature,
                        "temperature " + sample.temperature + " outside control band at t="
                                + sample.time);
            }
        } finally {
            TestSupport.deleteRecursively(directory);
        }
    }
}
