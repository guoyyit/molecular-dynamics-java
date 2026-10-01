package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyConservationTest {
    private static final double MAX_SECULAR_DRIFT_FRACTION = 5.0e-4;
    private static final double MAX_OSCILLATION_FRACTION = 3.0e-3;

    @Test
    void nveLongRunHasBoundedEnergyDrift() throws Exception {
        Path directory = TestSupport.freshDirectory("md-nve-drift");
        try {
            SimulationConfig config = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
            SimulationResult result = TestSupport.runFresh(config, directory);

            int start = result.samples.size() / 4;
            int end = result.samples.size() - 1;
            TrajectorySample first = result.samples.get(start);
            TrajectorySample last = result.samples.get(end);

            double referenceEnergy = StrictMath.abs(first.totalEnergy);
            double secularDrift = StrictMath.abs(last.totalEnergy - first.totalEnergy)
                    / referenceEnergy;
            assertTrue(secularDrift < MAX_SECULAR_DRIFT_FRACTION,
                    "secular energy drift " + secularDrift + " exceeds "
                            + MAX_SECULAR_DRIFT_FRACTION);

            double meanEnergy = 0.0;
            for (int i = start; i <= end; i++) {
                meanEnergy += result.samples.get(i).totalEnergy;
            }
            meanEnergy /= (end - start + 1);
            double maxOscillation = 0.0;
            for (int i = start; i <= end; i++) {
                maxOscillation = StrictMath.max(maxOscillation,
                        StrictMath.abs(result.samples.get(i).totalEnergy - meanEnergy));
            }
            double oscillationFraction = maxOscillation / referenceEnergy;
            assertTrue(oscillationFraction < MAX_OSCILLATION_FRACTION,
                    "energy oscillation " + oscillationFraction + " exceeds "
                            + MAX_OSCILLATION_FRACTION);
        } finally {
            TestSupport.deleteRecursively(directory);
        }
    }
}
