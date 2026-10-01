package com.github.guoyyit.moleculardynamicsjava.integrator;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationState;
import com.github.guoyyit.moleculardynamicsjava.physics.LennardJonesForce;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRng;

public interface Integrator {
    double step(SimulationState state, SimulationConfig config, DeterministicRng rng,
                LennardJonesForce forceField);
}
