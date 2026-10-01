package com.github.guoyyit.moleculardynamicsjava.config;

import com.github.guoyyit.moleculardynamicsjava.model.Particle;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRandom;

/**
 * Draws Maxwell-Boltzmann velocities at the configured target temperature
 * and removes the net center-of-mass drift. The random stream is consumed
 * in a fixed (particle, axis) order so identical seeds stay bit identical.
 */
public class VelocityInitializer {
    public VelocityInitializer() {
    }

    public void initializeVelocities(SimulationConfig config, DeterministicRandom random) {
        int count = config.particles.size();
        double totalMass = 0.0;
        double[] momentum = new double[3];
        for (Particle particle : config.particles) {
            double mass = config.species.get(particle.speciesIndex).mass;
            double sigma = StrictMath.sqrt(config.targetTemperature / mass);
            for (int axis = 0; axis < 3; axis++) {
                particle.velocity[axis] = sigma * random.nextGaussian();
                momentum[axis] += mass * particle.velocity[axis];
            }
            totalMass += mass;
        }
        for (Particle particle : config.particles) {
            double mass = config.species.get(particle.speciesIndex).mass;
            for (int axis = 0; axis < 3; axis++) {
                particle.velocity[axis] -= momentum[axis] / totalMass;
            }
        }
        rescale(config, count);
    }

    private void rescale(SimulationConfig config, int count) {
        double kinetic = 0.0;
        for (Particle particle : config.particles) {
            double mass = config.species.get(particle.speciesIndex).mass;
            kinetic += 0.5 * mass * (particle.velocity[0] * particle.velocity[0]
                    + particle.velocity[1] * particle.velocity[1]
                    + particle.velocity[2] * particle.velocity[2]);
        }
        double measuredTemperature = 2.0 * kinetic / (3.0 * count);
        if (measuredTemperature <= 0.0) {
            return;
        }
        double factor = StrictMath.sqrt(config.targetTemperature / measuredTemperature);
        for (Particle particle : config.particles) {
            particle.velocity[0] *= factor;
            particle.velocity[1] *= factor;
            particle.velocity[2] *= factor;
        }
    }
}
