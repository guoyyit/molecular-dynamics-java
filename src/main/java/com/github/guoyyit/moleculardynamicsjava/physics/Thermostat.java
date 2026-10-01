package com.github.guoyyit.moleculardynamicsjava.physics;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRandom;

/**
 * Langevin thermostat with the BAOAB operator splitting.
 *
 * <p>Each full step is B (half kick), A (half drift), O (Ornstein-Uhlenbeck
 * velocity collision), A, B. The integrator drives B/A; this class supplies
 * the stochastic O step and the two drift/kick helpers so the full step
 * stays in one place. Friction and target temperature come from the config.
 */
public class Thermostat {
    private final double targetTemperature;
    private final double damping;
    private final double noiseScale;
    private final double[] masses;
    private final DeterministicRandom random;

    public Thermostat(SimulationConfig config, DeterministicRandom random) {
        this.targetTemperature = config.targetTemperature;
        this.random = random;
        double friction = Math.max(0.0, config.thermostatFriction);
        double a = StrictMath.exp(-friction * config.timeStep);
        this.damping = a;
        this.noiseScale = StrictMath.sqrt(1.0 - a * a);
        int count = config.particles.size();
        masses = new double[count];
        for (int i = 0; i < count; i++) {
            masses[i] = config.species.get(config.particles.get(i).speciesIndex).mass;
        }
    }

    /** Half kick: velocities advanced by dt/2 under the given forces. */
    public void halfKick(double[][] velocities, double[][] forces, double timeStep) {
        double halfStep = 0.5 * timeStep;
        for (int i = 0; i < velocities.length; i++) {
            double coefficient = halfStep / masses[i];
            velocities[i][0] += coefficient * forces[i][0];
            velocities[i][1] += coefficient * forces[i][1];
            velocities[i][2] += coefficient * forces[i][2];
        }
    }

    /** Half drift: positions advanced by dt/2 with the current velocities. */
    public void halfDrift(double[][] positions, double[][] velocities, double timeStep,
                          PeriodicBox box) {
        double halfStep = 0.5 * timeStep;
        for (int i = 0; i < positions.length; i++) {
            positions[i][0] += halfStep * velocities[i][0];
            positions[i][1] += halfStep * velocities[i][1];
            positions[i][2] += halfStep * velocities[i][2];
            box.wrap(positions[i]);
        }
    }

    /** OU collision step applied to every velocity component. */
    public void apply(double[][] velocities) {
        for (int i = 0; i < velocities.length; i++) {
            double thermalVelocity = StrictMath.sqrt(targetTemperature / masses[i]);
            for (int axis = 0; axis < 3; axis++) {
                velocities[i][axis] = damping * velocities[i][axis]
                        + noiseScale * thermalVelocity * random.nextGaussian();
            }
        }
    }

    public double[] getMasses() {
        return masses;
    }
}
