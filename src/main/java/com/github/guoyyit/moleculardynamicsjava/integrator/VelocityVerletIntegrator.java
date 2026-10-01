package com.github.guoyyit.moleculardynamicsjava.integrator;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationState;
import com.github.guoyyit.moleculardynamicsjava.physics.LennardJonesForce;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRng;

public class VelocityVerletIntegrator implements Integrator {
    @Override
    public double step(SimulationState state, SimulationConfig config, DeterministicRng rng,
                       LennardJonesForce forceField) {
        double dt = config.getTimeStep();
        int n = state.n;
        double[] pos = state.pos;
        double[] vel = state.vel;
        double[] force = state.getForce();
        double[] mass = state.masses;
        double[] unwrapped = state.unwrapped;
        double[] box = config.getBox();
        for (int i = 0; i < n; i++) {
            double inverseMass = 1.0 / mass[i];
            for (int axis = 0; axis < 3; axis++) {
                int index = 3 * i + axis;
                vel[index] += 0.5 * dt * force[index] * inverseMass;
                double displacement = dt * vel[index];
                pos[index] += displacement;
                unwrapped[index] += displacement;
                pos[index] -= box[axis] * Math.floor(pos[index] / box[axis]);
            }
        }
        double potentialEnergy = forceField.computeForcesAndEnergy(state, config);
        for (int i = 0; i < n; i++) {
            double inverseMass = 1.0 / mass[i];
            for (int axis = 0; axis < 3; axis++) {
                int index = 3 * i + axis;
                vel[index] += 0.5 * dt * force[index] * inverseMass;
            }
        }
        state.step++;
        state.time += dt;
        return potentialEnergy;
    }
}
