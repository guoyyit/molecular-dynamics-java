package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.Particle;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Species;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivergenceTest {
    @Test
    void numericallyUnstableRunTerminatesAsDivergence(@TempDir Path directory) {
        SimulationConfig config = new SimulationConfig();
        config.boxLength = new double[] {10.0, 10.0, 10.0};
        config.species.add(new Species("A", 1.0, 1.0, 1.0));
        config.cutoff = 2.5;
        config.minSeparation = 0.5;
        config.ensemble = "NVE";
        config.timeStep = 0.5;
        config.duration = 1000.0;
        config.maxEnergyPerParticle = 500.0;
        config.sampleInterval = 1;
        config.checkpointInterval = 1000;
        config.particles.add(new Particle(0, new double[] {4.4, 5.0, 5.0}, new double[3]));
        config.particles.add(new Particle(0, new double[] {5.4, 5.0, 5.0}, new double[3]));
        config.particles.get(0).velocity[0] = 20.0;
        config.particles.get(1).velocity[0] = -20.0;

        SimulationEngine engine = new SimulationEngine(config, directory);
        SimulationResult result = engine.run(TestSupport.neverStop());

        assertEquals(TerminationReason.NUMERICAL_DIVERGENCE.name(), result.terminationReason);
        assertEquals(TerminationReason.NUMERICAL_DIVERGENCE, engine.getTerminationReason());
        assertTrue(result.steps > 0);
    }
}
