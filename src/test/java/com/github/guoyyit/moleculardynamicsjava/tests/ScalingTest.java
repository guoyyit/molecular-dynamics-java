package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ScalingTest {
    @TempDir
    Path directory;

    @Test
    void forceEvaluationScalesLinearlyWithCellList() {
        int[] cells = {6, 8};
        int[] sizes = {864, 2048};
        for (int c = 0; c < cells.length; c++) {
            TestSupport.runFresh(buildFccCube(cells[c]),
                    directory.resolve("warmf-" + sizes[c]));
        }
        double[] perStep = new double[sizes.length];
        for (int c = 0; c < cells.length; c++) {
            SimulationConfig config = buildFccCube(cells[c]);
            perStep[c] = measureForceCall(config, 150);
            System.out.printf("FORCE N=%d perCall=%.3fus perParticle=%.4fus%n",
                    sizes[c], perStep[c], perStep[c] / sizes[c]);
        }
        double ratio1 = (perStep[1] / sizes[1]) / (perStep[0] / sizes[0]);
        System.out.printf("per-particle growth ratio %.3f%n", ratio1);
        org.junit.jupiter.api.Assertions.assertTrue(ratio1 < 1.5,
                "cell-list per-particle cost grew by " + ratio1 + " from 864 to 2048");
    }

    private double measureForceCall(SimulationConfig config, int repetitions) {
        var potential = new com.github.guoyyit.moleculardynamicsjava.physics.LennardJones(config);
        double[][] positions = new double[config.particles.size()][3];
        int[] species = new int[config.particles.size()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = config.particles.get(i).position;
            species[i] = config.particles.get(i).speciesIndex;
        }
        potential.setSpeciesAt(species);
        potential.setPositions(positions);
        potential.computeForces();
        long start = System.nanoTime();
        for (int r = 0; r < repetitions; r++) {
            potential.computeForces();
        }
        return (System.nanoTime() - start) / 1.0e3 / repetitions;
    }

    private SimulationConfig buildFccCube(int cellsPerSide) {
        com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig config =
                new com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig();
        double a = 1.70;
        double box = cellsPerSide * a;
        config.boxLength = new double[] {box, box, box};
        config.species.add(new com.github.guoyyit.moleculardynamicsjava.model.Species(
                "Ar", 1.0, 1.0, 1.0));
        config.cutoff = 2.5;
        config.minSeparation = 0.85;
        config.timeStep = 0.003;
        config.duration = 0.003;
        double[][] basis = {{0, 0, 0}, {0.5, 0.5, 0}, {0.5, 0, 0.5}, {0, 0.5, 0.5}};
        for (int iz = 0; iz < cellsPerSide; iz++) {
            for (int iy = 0; iy < cellsPerSide; iy++) {
                for (int ix = 0; ix < cellsPerSide; ix++) {
                    for (double[] o : basis) {
                        config.particles.add(new com.github.guoyyit.moleculardynamicsjava.model.Particle(
                                0,
                                new double[] {(ix + o[0]) * a, (iy + o[1]) * a, (iz + o[2]) * a},
                                new double[3]));
                    }
                }
            }
        }
        return config;
    }
}
