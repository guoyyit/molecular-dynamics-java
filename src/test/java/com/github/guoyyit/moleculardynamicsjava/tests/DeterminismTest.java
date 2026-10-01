package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterminismTest {
    @Test
    void sameSeedProducesBitIdenticalNveTrajectories() throws Exception {
        Path dirA = TestSupport.freshDirectory("md-det-a");
        Path dirB = TestSupport.freshDirectory("md-det-b");
        try {
            SimulationConfig configA = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
            SimulationConfig configB = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);

            SimulationResult first = TestSupport.runFresh(configA, dirA);
            SimulationResult second = TestSupport.runFresh(configB, dirB);

            assertIdentical(first, second);
        } finally {
            TestSupport.deleteRecursively(dirA);
            TestSupport.deleteRecursively(dirB);
        }
    }

    @Test
    void sameSeedProducesBitIdenticalNvtTrajectories() throws Exception {
        Path dirA = TestSupport.freshDirectory("md-det-nvt-a");
        Path dirB = TestSupport.freshDirectory("md-det-nvt-b");
        try {
            SimulationConfig configA = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);
            SimulationConfig configB = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);

            SimulationResult first = TestSupport.runFresh(configA, dirA);
            SimulationResult second = TestSupport.runFresh(configB, dirB);

            assertIdentical(first, second);
        } finally {
            TestSupport.deleteRecursively(dirA);
            TestSupport.deleteRecursively(dirB);
        }
    }

    static void assertIdentical(SimulationResult first, SimulationResult second) {
        assertEquals(first.samples.size(), second.samples.size());
        for (int i = 0; i < first.samples.size(); i++) {
            TrajectorySample a = first.samples.get(i);
            TrajectorySample b = second.samples.get(i);
            assertEquals(a.step, b.step);
            assertEquals(a.time, b.time, 0.0);
            assertEquals(a.temperature, b.temperature, 0.0);
            assertEquals(a.potentialEnergy, b.potentialEnergy, 0.0);
            assertEquals(a.kineticEnergy, b.kineticEnergy, 0.0);
            assertEquals(a.totalEnergy, b.totalEnergy, 0.0);
        }
        assertDoubleArraysEqual(first.rdfValues, second.rdfValues);
        assertDoubleArraysEqual(first.msdValues, second.msdValues);
    }

    static void assertDoubleArraysEqual(double[] a, double[] b) {
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0.0);
        }
    }
}
