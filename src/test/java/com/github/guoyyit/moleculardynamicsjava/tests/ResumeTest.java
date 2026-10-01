package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeTest {
    @Test
    void interruptedAndResumedNvtRunMatchesUninterruptedRun() throws Exception {
        Path referenceDir = TestSupport.freshDirectory("md-resume-ref");
        Path segmentedDir = TestSupport.freshDirectory("md-resume-seg");
        try {
            SimulationConfig referenceConfig = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);
            SimulationConfig segmentedConfig = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);

            SimulationResult reference = TestSupport.runFresh(referenceConfig, referenceDir);

            long totalSteps = StrictMath.round(
                    segmentedConfig.duration / segmentedConfig.timeStep);
            long stopAt = totalSteps / 3;
            SimulationResult firstSegment = runUntil(segmentedConfig, segmentedDir, stopAt);
            assertEquals(TerminationReason.CANCELLED.name(), firstSegment.terminationReason);
            assertTrue(firstSegment.steps >= stopAt);

            SimulationResult secondSegment = runUntil(segmentedConfig, segmentedDir, totalSteps);
            assertEquals(TerminationReason.COMPLETED.name(), secondSegment.terminationReason);
            assertEquals(totalSteps, secondSegment.steps);

            DeterminismTest.assertIdentical(reference, secondSegment);
        } finally {
            TestSupport.deleteRecursively(referenceDir);
            TestSupport.deleteRecursively(segmentedDir);
        }
    }

    private SimulationResult runUntil(SimulationConfig config, Path directory, long stopAtStep) {
        SimulationEngine engine = new SimulationEngine(config, directory);
        return engine.run(new SimulationEngine.RunControl() {
            @Override
            public boolean isCancelled(long completedSteps) {
                return completedSteps >= stopAtStep;
            }

            @Override
            public boolean isTimedOut(long completedSteps) {
                return false;
            }
        });
    }
}
