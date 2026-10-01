package com.github.guoyyit.moleculardynamicsjava.examples;

import com.github.guoyyit.moleculardynamicsjava.model.Particle;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Species;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in, fully parameterized example systems.
 *
 * <p>Every example places a single Lennard-Jones species on a perfect FCC
 * lattice (reduced units sigma=epsilon=mass=1). Velocities are drawn
 * deterministically from the seed, so each named example is reproducible
 * bit for bit. The names and parameters are part of the acceptance
 * contract and must not be changed silently.
 */
public class BuiltInExamples {
    public static final String FCC_32_NVE = "fcc32-nve";
    public static final String FCC_108_NVT = "fcc108-nvt";
    public static final String FCC_256_BENCH = "fcc256-bench";
    public static final String FCC_500_BENCH = "fcc500-bench";

    public BuiltInExamples() {
    }

    public Map<String, SimulationConfig> all() {
        Map<String, SimulationConfig> examples = new LinkedHashMap<>();
        examples.put(FCC_32_NVE, fcc32Nve());
        examples.put(FCC_108_NVT, fcc108Nvt());
        examples.put(FCC_256_BENCH, fcc256Bench());
        examples.put(FCC_500_BENCH, fcc500Bench());
        return examples;
    }

    public SimulationConfig get(String name) {
        return all().get(name);
    }

    private SimulationConfig fcc32Nve() {
        SimulationConfig config = baseFcc("fcc32-nve", 2, 1.55, 1.5);
        config.ensemble = "NVE";
        config.timeStep = 0.002;
        config.duration = 2.0;
        config.targetTemperature = 0.5;
        config.seed = 20240501L;
        config.sampleInterval = 25;
        config.checkpointInterval = 100;
        return config;
    }

    private SimulationConfig fcc108Nvt() {
        SimulationConfig config = baseFcc("fcc108-nvt", 3, 1.70, 2.5);
        config.ensemble = "NVT";
        config.targetTemperature = 1.0;
        config.thermostatFriction = 1.0;
        config.timeStep = 0.003;
        config.duration = 3.0;
        config.seed = 20240502L;
        config.sampleInterval = 25;
        config.checkpointInterval = 150;
        return config;
    }

    private SimulationConfig fcc256Bench() {
        SimulationConfig config = baseFcc("fcc256-bench", 4, 1.70, 2.5);
        config.ensemble = "NVT";
        config.targetTemperature = 1.2;
        config.thermostatFriction = 1.0;
        config.timeStep = 0.003;
        config.duration = 1.0;
        config.seed = 20240503L;
        config.sampleInterval = 50;
        config.checkpointInterval = 250;
        return config;
    }

    private SimulationConfig fcc500Bench() {
        SimulationConfig config = baseFcc("fcc500-bench", 5, 1.70, 2.5);
        config.ensemble = "NVT";
        config.targetTemperature = 1.2;
        config.thermostatFriction = 1.0;
        config.timeStep = 0.003;
        config.duration = 0.3;
        config.seed = 20240504L;
        config.sampleInterval = 100;
        config.checkpointInterval = 100;
        return config;
    }

    private SimulationConfig baseFcc(String jobId, int cellsPerSide, double latticeConstant,
                                     double cutoff) {
        SimulationConfig config = new SimulationConfig();
        config.jobId = jobId;
        double boxLength = cellsPerSide * latticeConstant;
        config.boxLength = new double[] {boxLength, boxLength, boxLength};
        config.species.add(new Species("Ar", 1.0, 1.0, 1.0));
        config.cutoff = cutoff;
        config.minSeparation = 0.85;
        config.targetTemperature = 1.0;
        double[][] basis = {
                {0.0, 0.0, 0.0},
                {0.5, 0.5, 0.0},
                {0.5, 0.0, 0.5},
                {0.0, 0.5, 0.5}
        };
        for (int iz = 0; iz < cellsPerSide; iz++) {
            for (int iy = 0; iy < cellsPerSide; iy++) {
                for (int ix = 0; ix < cellsPerSide; ix++) {
                    for (double[] offset : basis) {
                        double[] position = {
                                (ix + offset[0]) * latticeConstant,
                                (iy + offset[1]) * latticeConstant,
                                (iz + offset[2]) * latticeConstant
                        };
                        if (position[0] >= boxLength) {
                            position[0] -= boxLength;
                        }
                        if (position[1] >= boxLength) {
                            position[1] -= boxLength;
                        }
                        if (position[2] >= boxLength) {
                            position[2] -= boxLength;
                        }
                        config.particles.add(new Particle(0, position, new double[3]));
                    }
                }
            }
        }
        return config;
    }
}
