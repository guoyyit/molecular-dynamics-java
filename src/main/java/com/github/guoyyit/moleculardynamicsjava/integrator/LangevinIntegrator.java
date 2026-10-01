package com.github.guoyyit.moleculardynamicsjava.integrator;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationState;
import com.github.guoyyit.moleculardynamicsjava.physics.LennardJonesForce;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRng;

public class LangevinIntegrator implements Integrator {
    @Override
    public double step(SimulationState state, SimulationConfig config, DeterministicRng rng,
                       LennardJonesForce forceField) {
        double dt = config.getTimeStep();
        double gamma = config.getThermostatFriction();
        double targetTemperature = config.getTargetTemperature();
        double damping = Math.exp(-gamma * dt);
        double noiseScale = Math.sqrt(targetTemperature * (1.0 - damping * damping));
        int n = state.n;
        double[] pos = state.pos;
        double[] vel = state.vel;
        double[] force = state.getForce();
        double[] mass = state.masses;
        double[] unwrapped = state.unwrapped;
        double[] box = config.getBox();

        kick(state, n, vel, force, mass, 0.5 * dt);
        drift(state, n, pos, vel, unwrapped, box, 0.5 * dt);
        for (int i = 0; i < n; i++) {
            double velocityScale = noiseScale / Math.sqrt(mass[i]);
            for (int axis = 0; axis < 3; axis++) {
                int index = 3 * i + axis;
                vel[index] = damping * vel[index] + velocityScale * rng.nextGaussian();
            }
        }
        drift(state, n, pos, vel, unwrapped, box, 0.5 * dt);
        double potentialEnergy = forceField.computeForcesAndEnergy(state, config);
        kick(state, n, vel, force, mass, 0.5 * dt);

        state.step++;
        state.time += dt;
        return potentialEnergy;
    }

    private void kick(SimulationState state, int n, double[] vel, double[] force,
                      double[] mass, double halfDt) {
        for (int i = 0; i < n; i++) {
            double inverseMass = 1.0 / mass[i];
            for (int axis = 0; axis < 3; axis++) {
                int index = 3 * i + axis;
                vel[index] += halfDt * force[index] * inverseMass;
            }
        }
    }

    private void drift(SimulationState state, int n, double[] pos, double[] vel,
                       double[] unwrapped, double[] box, double halfDt) {
        for (int i = 0; i < n; i++) {
            for (int axis = 0; axis < 3; axis++) {
                int index = 3 * i + axis;
                double displacement = halfDt * vel[index];
                pos[index] += displacement;
                unwrapped[index] += displacement;
                pos[index] -= box[axis] * Math.floor(pos[index] / box[axis]);
            }
        }
    }
}
