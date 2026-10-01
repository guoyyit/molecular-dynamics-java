package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins statistical reference values of the built-in acceptance systems.
 * The exact scalars are golden values for the current deterministic
 * algorithm; changing the physics requires regenerating them deliberately.
 */
class ReferenceStatisticsTest {
    @TempDir
    Path directory;

    @Test
    void fcc32NveMatchesGoldenReference() {
        SimulationConfig config = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
        SimulationResult result = TestSupport.runFresh(config, directory.resolve("ref-nve"));

        assertEquals(1000, result.steps);
        assertEquals(41, result.samples.size());
        assertEquals(-101.95383768158155,
                result.samples.get(0).totalEnergy, 1.0e-9);
        assertEquals(-101.92946249028363,
                result.samples.get(result.samples.size() - 1).totalEnergy, 1.0e-9);

        int peakBin = peakBinOf(result);
        assertEquals(1.09275, result.rdfBinEdges[peakBin], 1.0e-4);
        assertEquals(8.86556471, result.rdfValues[peakBin], 1.0e-6);

        assertEquals(4, result.msdValues.length);
        assertEquals(0.5, result.msdTimes[0], 1.0e-12);
        assertEquals(0.0017599061553619968, result.msdValues[0], 1.0e-9);
        assertEquals(0.002455406467611735, result.msdValues[3], 1.0e-9);
    }

    @Test
    void fcc108NvtMatchesGoldenReference() {
        SimulationConfig config = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);
        SimulationResult result = TestSupport.runFresh(config, directory.resolve("ref-nvt"));

        assertEquals(1000, result.steps);
        double meanTemperature = meanTail(result);
        assertEquals(0.9863446583407509, meanTemperature, 1.0e-9);

        TrajectorySample last = result.samples.get(result.samples.size() - 1);
        assertEquals(0.9307335224436981, last.temperature, 1.0e-9);

        assertEquals(0.5723566590116134, result.msdValues[3], 1.0e-6);
    }

    private static int peakBinOf(SimulationResult result) {
        int peakBin = 0;
        for (int i = 0; i < result.rdfValues.length; i++) {
            if (result.rdfValues[i] > result.rdfValues[peakBin]) {
                peakBin = i;
            }
        }
        return peakBin;
    }

    private static double meanTail(SimulationResult result) {
        int half = result.samples.size() / 2;
        double mean = 0.0;
        for (int i = half; i < result.samples.size(); i++) {
            mean += result.samples.get(i).temperature;
        }
        return mean / (result.samples.size() - half);
    }
}
