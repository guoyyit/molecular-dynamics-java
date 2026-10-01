package com.github.guoyyit.moleculardynamicsjava.physics;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRandom;

/**
 * Time integration state.
 *
 * <p>NVE runs use the velocity Verlet algorithm. NVT runs use the BAOAB
 * Langevin splitting implemented in {@link Thermostat}; in that case the
 * sequence per step is B(half), A(half), O, A(half), B(half).
 */
public class Integrator {
    private final double timeStep;
    private final boolean nvt;
    private final double[][] positions;
    private final double[][] velocities;
    private final double[][] unwrappedPositions;
    private final double[] masses;
    private final LennardJones potential;
    private final Thermostat thermostat;

    private double potentialEnergy;

    public Integrator(SimulationConfig config, DeterministicRandom random,
                      LennardJones potential, Thermostat thermostat) {
        this.timeStep = config.timeStep;
        this.nvt = "NVT".equalsIgnoreCase(config.ensemble);
        this.potential = potential;
        this.thermostat = thermostat;
        int count = config.particles.size();
        positions = new double[count][3];
        velocities = new double[count][3];
        unwrappedPositions = new double[count][3];
        masses = thermostat.getMasses();
        for (int i = 0; i < count; i++) {
            System.arraycopy(config.particles.get(i).position, 0, positions[i], 0, 3);
            System.arraycopy(config.particles.get(i).velocity, 0, velocities[i], 0, 3);
            System.arraycopy(positions[i], 0, unwrappedPositions[i], 0, 3);
        }
        potential.setPositions(positions);
        potential.setSpeciesAt(buildSpeciesIndices(config));
        potential.getBox().wrapAll(positions);
        potentialEnergy = potential.computeForces();
    }

    private static int[] buildSpeciesIndices(SimulationConfig config) {
        int count = config.particles.size();
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = config.particles.get(i).speciesIndex;
        }
        return indices;
    }

    public double[][] getPositions() {
        return positions;
    }

    public double[][] getVelocities() {
        return velocities;
    }

    public double[][] getUnwrappedPositions() {
        return unwrappedPositions;
    }

    public double[] getMasses() {
        return masses;
    }

    public PeriodicBox getBox() {
        return potential.getBox();
    }

    public double getPotentialEnergy() {
        return potentialEnergy;
    }

    /**
     * Restore dynamic state from a checkpoint and recompute forces.
     * Used only when continuing a previously committed run.
     */
    public void restore(double[][] savedPositions, double[][] savedUnwrapped,
                        double[][] savedVelocities) {
        for (int i = 0; i < positions.length; i++) {
            System.arraycopy(savedPositions[i], 0, positions[i], 0, 3);
            System.arraycopy(savedUnwrapped[i], 0, unwrappedPositions[i], 0, 3);
            System.arraycopy(savedVelocities[i], 0, velocities[i], 0, 3);
            potential.getBox().wrap(positions[i]);
        }
        potential.setPositions(positions);
        potentialEnergy = potential.computeForces();
    }

    /** Advance the system by one time step. */
    public void step() {
        double[][] forces = potential.getForces();
        if (nvt) {
            thermostat.halfKick(velocities, forces, timeStep);
            halfDriftNvt(0.5);
            thermostat.apply(velocities);
            halfDriftNvt(0.5);
            potential.setPositions(positions);
            potentialEnergy = potential.computeForces();
            thermostat.halfKick(velocities, potential.getForces(), timeStep);
        } else {
            for (int i = 0; i < positions.length; i++) {
                double coefficient = 0.5 * timeStep / masses[i];
                velocities[i][0] += coefficient * forces[i][0];
                velocities[i][1] += coefficient * forces[i][1];
                velocities[i][2] += coefficient * forces[i][2];
                advancePosition(i, timeStep);
            }
            potential.setPositions(positions);
            potentialEnergy = potential.computeForces();
            double[][] newForces = potential.getForces();
            for (int i = 0; i < positions.length; i++) {
                double coefficient = 0.5 * timeStep / masses[i];
                velocities[i][0] += coefficient * newForces[i][0];
                velocities[i][1] += coefficient * newForces[i][1];
                velocities[i][2] += coefficient * newForces[i][2];
            }
        }
    }

    private void halfDriftNvt(double fraction) {
        double step = fraction * timeStep;
        for (int i = 0; i < positions.length; i++) {
            advancePosition(i, step);
        }
    }

    private void advancePosition(int i, double step) {
        positions[i][0] += step * velocities[i][0];
        positions[i][1] += step * velocities[i][1];
        positions[i][2] += step * velocities[i][2];
        unwrappedPositions[i][0] += step * velocities[i][0];
        unwrappedPositions[i][1] += step * velocities[i][1];
        unwrappedPositions[i][2] += step * velocities[i][2];
        potential.getBox().wrap(positions[i]);
    }
}
