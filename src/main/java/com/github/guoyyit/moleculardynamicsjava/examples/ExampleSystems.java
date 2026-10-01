package com.github.guoyyit.moleculardynamicsjava.examples;

import com.github.guoyyit.moleculardynamicsjava.model.Ensemble;
import com.github.guoyyit.moleculardynamicsjava.model.ParticleSpec;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Species;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRng;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class ExampleSystems {
    private ExampleSystems() {
    }

    public static Map<String, SimulationConfig> all() {
        Map<String, SimulationConfig> examples = new LinkedHashMap<>();
        examples.put("argon-fcc-nve", argonFcc(3, Ensemble.NVE, 2000L, 42L));
        examples.put("argon-fcc-nvt", argonFcc(3, Ensemble.NVT, 2000L, 42L));
        examples.put("argon-fcc-medium", argonFcc(4, Ensemble.NVE, 200L, 7L));
        return examples;
    }

    public static SimulationConfig argonFcc(int cellsPerAxis, Ensemble ensemble,
                                            long steps, long seed) {
        SimulationConfig config = new SimulationConfig();
        double reducedDensity = 0.8;
        int particlesPerCell = 4;
        int n = particlesPerCell * cellsPerAxis * cellsPerAxis * cellsPerAxis;
        double volume = n / reducedDensity;
        double boxLength = Math.cbrt(volume);
        double latticeConstant = boxLength / cellsPerAxis;
        config.setName("argon-fcc-" + n + "-" + ensemble.name().toLowerCase());
        config.setBox(new double[]{boxLength, boxLength, boxLength});
        config.setSpecies(List.of(new Species("Ar", 1.0)));
        config.setEpsilon(1.0);
        config.setSigma(1.0);
        config.setCutoff(2.5);
        config.setEnsemble(ensemble);
        config.setTimeStep(0.004);
        config.setSteps(steps);
        config.setSeed(seed);
        config.setMinSeparation(0.85);
        config.setSampleInterval(20L);
        config.setCheckpointInterval(500L);
        config.setRetainedCheckpoints(3);
        config.setRdfBins(400);
        config.setMaxTemperature(50.0);

        double[][] basis = {
                {0.0, 0.0, 0.0},
                {0.5, 0.5, 0.0},
                {0.5, 0.0, 0.5},
                {0.0, 0.5, 0.5}
        };
        List<ParticleSpec> particles = new ArrayList<>(n);
        DeterministicRng rng = new DeterministicRng(seed);
        double targetTemperature = 1.5;
        for (int iz = 0; iz < cellsPerAxis; iz++) {
            for (int iy = 0; iy < cellsPerAxis; iy++) {
                for (int ix = 0; ix < cellsPerAxis; ix++) {
                    for (double[] site : basis) {
                        double[] position = new double[]{
                                (ix + site[0]) * latticeConstant,
                                (iy + site[1]) * latticeConstant,
                                (iz + site[2]) * latticeConstant
                        };
                        double[] velocity = new double[]{
                                rng.nextGaussian() * Math.sqrt(targetTemperature),
                                rng.nextGaussian() * Math.sqrt(targetTemperature),
                                rng.nextGaussian() * Math.sqrt(targetTemperature)
                        };
                        particles.add(new ParticleSpec("Ar", position, velocity));
                    }
                }
            }
        }
        config.setParticles(particles);
        config.setTargetTemperature(ensemble == Ensemble.NVT ? targetTemperature : null);
        config.setThermostatFriction(1.0);
        return config;
    }
}
