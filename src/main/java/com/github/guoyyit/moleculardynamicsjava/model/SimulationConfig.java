package com.github.guoyyit.moleculardynamicsjava.model;

import java.util.ArrayList;
import java.util.List;

public class SimulationConfig {
    private String name = "simulation";
    private double[] box = {10.0, 10.0, 10.0};
    private List<Species> species = new ArrayList<>();
    private List<ParticleSpec> particles = new ArrayList<>();
    private double epsilon = 1.0;
    private double sigma = 1.0;
    private double cutoff = 2.5;
    private Ensemble ensemble = Ensemble.NVE;
    private double timeStep = 0.005;
    private long steps = 1000;
    private Double targetTemperature = null;
    private double thermostatFriction = 1.0;
    private long seed = 1234L;
    private double minSeparation = 0.87;
    private long sampleInterval = 10L;
    private long checkpointInterval = 500L;
    private int retainedCheckpoints = 3;
    private long timeoutMillis = 0L;
    private Double maxTemperature = 100.0;
    private double maxUnwrappedDistance = 1.0e6;
    private int rdfBins = 400;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double[] getBox() { return box; }
    public void setBox(double[] box) { this.box = box; }
    public List<Species> getSpecies() { return species; }
    public void setSpecies(List<Species> species) { this.species = species; }
    public List<ParticleSpec> getParticles() { return particles; }
    public void setParticles(List<ParticleSpec> particles) { this.particles = particles; }
    public double getEpsilon() { return epsilon; }
    public void setEpsilon(double epsilon) { this.epsilon = epsilon; }
    public double getSigma() { return sigma; }
    public void setSigma(double sigma) { this.sigma = sigma; }
    public double getCutoff() { return cutoff; }
    public void setCutoff(double cutoff) { this.cutoff = cutoff; }
    public Ensemble getEnsemble() { return ensemble; }
    public void setEnsemble(Ensemble ensemble) { this.ensemble = ensemble; }
    public double getTimeStep() { return timeStep; }
    public void setTimeStep(double timeStep) { this.timeStep = timeStep; }
    public long getSteps() { return steps; }
    public void setSteps(long steps) { this.steps = steps; }
    public Double getTargetTemperature() { return targetTemperature; }
    public void setTargetTemperature(Double targetTemperature) { this.targetTemperature = targetTemperature; }
    public double getThermostatFriction() { return thermostatFriction; }
    public void setThermostatFriction(double thermostatFriction) { this.thermostatFriction = thermostatFriction; }
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public double getMinSeparation() { return minSeparation; }
    public void setMinSeparation(double minSeparation) { this.minSeparation = minSeparation; }
    public long getSampleInterval() { return sampleInterval; }
    public void setSampleInterval(long sampleInterval) { this.sampleInterval = sampleInterval; }
    public long getCheckpointInterval() { return checkpointInterval; }
    public void setCheckpointInterval(long checkpointInterval) { this.checkpointInterval = checkpointInterval; }
    public int getRetainedCheckpoints() { return retainedCheckpoints; }
    public void setRetainedCheckpoints(int retainedCheckpoints) { this.retainedCheckpoints = retainedCheckpoints; }
    public long getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public Double getMaxTemperature() { return maxTemperature; }
    public void setMaxTemperature(Double maxTemperature) { this.maxTemperature = maxTemperature; }
    public double getMaxUnwrappedDistance() { return maxUnwrappedDistance; }
    public void setMaxUnwrappedDistance(double maxUnwrappedDistance) { this.maxUnwrappedDistance = maxUnwrappedDistance; }
    public int getRdfBins() { return rdfBins; }
    public void setRdfBins(int rdfBins) { this.rdfBins = rdfBins; }
}
