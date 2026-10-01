package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.config.ConfigValidator;
import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.Particle;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Species;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void builtInExamplesAreValid() {
        new BuiltInExamples().all().forEach((name, config) ->
                assertTrue(validator.validate(config).isEmpty(),
                        "example " + name + " should be valid: " + validator.validate(config)));
    }

    @Test
    void rejectsNegativeMass() {
        SimulationConfig config = minimalValidConfig();
        config.species.get(0).mass = -1.0;
        assertContains(validator.validate(config), "mass");
    }

    @Test
    void rejectsZeroMass() {
        SimulationConfig config = minimalValidConfig();
        config.species.get(0).mass = 0.0;
        assertContains(validator.validate(config), "mass");
    }

    @Test
    void rejectsOutOfBoundsPosition() {
        SimulationConfig config = minimalValidConfig();
        config.particles.get(0).position[1] = 10.1;
        assertContains(validator.validate(config), "outside the box");
    }

    @Test
    void rejectsNegativePosition() {
        SimulationConfig config = minimalValidConfig();
        config.particles.get(0).position[0] = -0.01;
        assertContains(validator.validate(config), "outside the box");
    }

    @Test
    void rejectsOverlappingParticles() {
        SimulationConfig config = minimalValidConfig();
        config.minSeparation = 1.0;
        config.particles.get(1).position = new double[] {1.05, 1.0, 1.0};
        assertContains(validator.validate(config), "overlap");
    }

    @Test
    void acceptsParticlesAtMinSeparationBoundary() {
        SimulationConfig config = minimalValidConfig();
        config.minSeparation = 1.0;
        config.particles.get(1).position = new double[] {2.0, 1.0, 1.0};
        assertTrue(validator.validate(config).isEmpty());
    }

    @Test
    void rejectsCutoffBeyondHalfBox() {
        SimulationConfig config = minimalValidConfig();
        config.cutoff = 6.0;
        assertContains(validator.validate(config), "cutoff");
    }

    @Test
    void rejectsUnknownEnsemble() {
        SimulationConfig config = minimalValidConfig();
        config.ensemble = "NPT";
        assertContains(validator.validate(config), "ensemble");
    }

    @Test
    void rejectsNonPositiveTimeStepAndDuration() {
        SimulationConfig zeroStep = minimalValidConfig();
        zeroStep.timeStep = 0.0;
        assertContains(validator.validate(zeroStep), "timeStep");

        SimulationConfig negativeDuration = minimalValidConfig();
        negativeDuration.duration = -1.0;
        assertContains(validator.validate(negativeDuration), "duration");
    }

    @Test
    void rejectsInvalidSpeciesIndex() {
        SimulationConfig config = minimalValidConfig();
        config.particles.get(0).speciesIndex = 5;
        assertContains(validator.validate(config), "speciesIndex");
    }

    private void assertContains(List<String> errors, String fragment) {
        assertFalse(errors.isEmpty(), "expected validation errors containing '" + fragment + "'");
        assertTrue(errors.stream().anyMatch(e -> e.contains(fragment)),
                "expected an error containing '" + fragment + "', got " + errors);
    }

    private SimulationConfig minimalValidConfig() {
        SimulationConfig config = new SimulationConfig();
        config.boxLength = new double[] {10.0, 10.0, 10.0};
        config.species.add(new Species("A", 1.0, 1.0, 1.0));
        config.cutoff = 2.5;
        config.minSeparation = 0.8;
        config.timeStep = 0.005;
        config.duration = 0.1;
        config.ensemble = "NVE";
        config.sampleInterval = 10;
        config.checkpointInterval = 100;
        config.particles.add(new Particle(0, new double[] {1.0, 1.0, 1.0}, new double[3]));
        config.particles.add(new Particle(0, new double[] {3.0, 1.0, 1.0}, new double[3]));
        return config;
    }
}
