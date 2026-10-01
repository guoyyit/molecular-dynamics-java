package com.github.guoyyit.moleculardynamicsjava.model;

import java.util.ArrayList;
import java.util.List;

public class SimulationConfig {
    public String jobId;
    public double[] boxLength = new double[3];
    public List<Species> species = new ArrayList<>();
    public List<Particle> particles = new ArrayList<>();
    public double cutoff = 2.5;
    public double minSeparation = 0.8;
    public long seed = 1L;
    public String ensemble = "NVE";
    public double timeStep = 0.005;
    public double duration = 1.0;
    public double targetTemperature = 1.0;
    public double thermostatFriction = 1.0;
    public long sampleInterval = 10;
    public long checkpointInterval = 100;
    public double timeoutMillis = 0;
    public double maxEnergyPerParticle = 1.0e6;

    public SimulationConfig() {
    }
}
