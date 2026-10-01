package com.github.guoyyit.moleculardynamicsjava.config;

import com.github.guoyyit.moleculardynamicsjava.model.Particle;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Species;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup validation of user supplied simulation configurations.
 * All problems are collected so callers receive a complete, readable list.
 */
public class ConfigValidator {
    public ConfigValidator() {
    }

    public List<String> validate(SimulationConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("configuration must not be null");
            return errors;
        }
        if (config.boxLength == null || config.boxLength.length != 3) {
            errors.add("boxLength must contain exactly three values [Lx, Ly, Lz]");
            return errors;
        }
        for (int axis = 0; axis < 3; axis++) {
            if (!(config.boxLength[axis] > 0.0) || Double.isNaN(config.boxLength[axis])) {
                errors.add("boxLength[" + axis + "] must be a finite positive number");
            }
        }
        if (config.species == null || config.species.isEmpty()) {
            errors.add("at least one species must be defined");
            return errors;
        }
        for (int a = 0; a < config.species.size(); a++) {
            Species species = config.species.get(a);
            if (species == null) {
                errors.add("species[" + a + "] must not be null");
                continue;
            }
            if (!(species.mass > 0.0)) {
                errors.add("species[" + a + "] (" + species.name + ") mass must be positive, got "
                        + species.mass);
            }
            if (!(species.sigma > 0.0)) {
                errors.add("species[" + a + "] (" + species.name + ") sigma must be positive, got "
                        + species.sigma);
            }
            if (!(species.epsilon > 0.0)) {
                errors.add("species[" + a + "] (" + species.name + ") epsilon must be positive, got "
                        + species.epsilon);
            }
        }
        if (config.particles == null || config.particles.isEmpty()) {
            errors.add("at least one particle must be defined");
            return errors;
        }
        if (!(config.cutoff > 0.0)) {
            errors.add("cutoff must be positive, got " + config.cutoff);
        }
        double halfMinBox = 0.5 * Math.min(config.boxLength[0],
                Math.min(config.boxLength[1], config.boxLength[2]));
        if (config.cutoff >= halfMinBox) {
            errors.add("cutoff (" + config.cutoff + ") must be smaller than half the shortest box "
                    + "edge (" + halfMinBox + ") for the minimum-image convention");
        }
        if (!(config.minSeparation > 0.0)) {
            errors.add("minSeparation must be positive, got " + config.minSeparation);
        }
        if (!(config.timeStep > 0.0)) {
            errors.add("timeStep must be positive, got " + config.timeStep);
        }
        if (!(config.duration > 0.0)) {
            errors.add("duration must be positive, got " + config.duration);
        }
        if (!"NVE".equalsIgnoreCase(config.ensemble) && !"NVT".equalsIgnoreCase(config.ensemble)) {
            errors.add("ensemble must be NVE or NVT, got '" + config.ensemble + "'");
        }
        if ("NVT".equalsIgnoreCase(config.ensemble) && !(config.targetTemperature > 0.0)) {
            errors.add("targetTemperature must be positive in NVT mode, got "
                    + config.targetTemperature);
        }
        if (config.thermostatFriction < 0.0) {
            errors.add("thermostatFriction must not be negative, got " + config.thermostatFriction);
        }
        if (config.sampleInterval <= 0) {
            errors.add("sampleInterval must be a positive integer, got " + config.sampleInterval);
        }
        if (config.checkpointInterval <= 0) {
            errors.add("checkpointInterval must be a positive integer, got "
                    + config.checkpointInterval);
        }
        validateParticles(config, errors);
        return errors;
    }

    private void validateParticles(SimulationConfig config, List<String> errors) {
        List<Particle> particles = config.particles;
        double minSquared = config.minSeparation * config.minSeparation;
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            if (particle == null) {
                errors.add("particles[" + i + "] must not be null");
                continue;
            }
            if (particle.speciesIndex < 0 || particle.speciesIndex >= config.species.size()) {
                errors.add("particles[" + i + "] speciesIndex " + particle.speciesIndex
                        + " is out of range [0," + config.species.size() + ")");
            }
            if (particle.position == null || particle.position.length != 3
                    || particle.velocity == null || particle.velocity.length != 3) {
                errors.add("particles[" + i + "] position and velocity must each have 3 components");
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                double coordinate = particle.position[axis];
                if (Double.isNaN(coordinate) || coordinate < 0.0
                        || coordinate >= config.boxLength[axis]) {
                    errors.add("particles[" + i + "] position[" + axis + "]=" + coordinate
                            + " is outside the box [0," + config.boxLength[axis] + ")");
                }
            }
        }
        for (int i = 0; i < particles.size(); i++) {
            Particle pi = particles.get(i);
            if (pi == null || pi.position == null) {
                continue;
            }
            for (int j = i + 1; j < particles.size(); j++) {
                Particle pj = particles.get(j);
                if (pj == null || pj.position == null) {
                    continue;
                }
                double dx = pi.position[0] - pj.position[0];
                double dy = pi.position[1] - pj.position[1];
                double dz = pi.position[2] - pj.position[2];
                dx -= config.boxLength[0] * StrictMath.rint(dx / config.boxLength[0]);
                dy -= config.boxLength[1] * StrictMath.rint(dy / config.boxLength[1]);
                dz -= config.boxLength[2] * StrictMath.rint(dz / config.boxLength[2]);
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < minSquared) {
                    errors.add("particles " + i + " and " + j + " overlap: minimum-image distance "
                            + StrictMath.sqrt(distanceSquared) + " is below minSeparation "
                            + config.minSeparation);
                }
            }
        }
    }
}
