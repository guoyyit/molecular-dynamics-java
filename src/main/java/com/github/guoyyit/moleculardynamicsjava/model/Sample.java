package com.github.guoyyit.moleculardynamicsjava.model;

public record Sample(long step, double time, double temperature,
                     double kineticEnergy, double potentialEnergy, double totalEnergy) {
}
