package com.github.guoyyit.moleculardynamicsjava.model;

import java.util.ArrayList;
import java.util.List;

public class SimulationResult {
    private String jobId;
    private String name;
    private Ensemble ensemble;
    private int particles;
    private long steps;
    private double finalTime;
    private TerminationReason terminationReason = TerminationReason.COMPLETED;
    private String message = "";
    private List<Sample> samples = new ArrayList<>();
    private double[] rdfEdges = new double[0];
    private double[] rdfValues = new double[0];
    private double[] msdTimes = new double[0];
    private double[] msdValues = new double[0];
    private double meanTemperature;
    private double meanTotalEnergy;
    private double energyDriftPerParticle;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Ensemble getEnsemble() { return ensemble; }
    public void setEnsemble(Ensemble ensemble) { this.ensemble = ensemble; }
    public int getParticles() { return particles; }
    public void setParticles(int particles) { this.particles = particles; }
    public long getSteps() { return steps; }
    public void setSteps(long steps) { this.steps = steps; }
    public double getFinalTime() { return finalTime; }
    public void setFinalTime(double finalTime) { this.finalTime = finalTime; }
    public TerminationReason getTerminationReason() { return terminationReason; }
    public void setTerminationReason(TerminationReason terminationReason) { this.terminationReason = terminationReason; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<Sample> getSamples() { return samples; }
    public void setSamples(List<Sample> samples) { this.samples = samples; }
    public double[] getRdfEdges() { return rdfEdges; }
    public void setRdfEdges(double[] rdfEdges) { this.rdfEdges = rdfEdges; }
    public double[] getRdfValues() { return rdfValues; }
    public void setRdfValues(double[] rdfValues) { this.rdfValues = rdfValues; }
    public double[] getMsdTimes() { return msdTimes; }
    public void setMsdTimes(double[] msdTimes) { this.msdTimes = msdTimes; }
    public double[] getMsdValues() { return msdValues; }
    public void setMsdValues(double[] msdValues) { this.msdValues = msdValues; }
    public double getMeanTemperature() { return meanTemperature; }
    public void setMeanTemperature(double meanTemperature) { this.meanTemperature = meanTemperature; }
    public double getMeanTotalEnergy() { return meanTotalEnergy; }
    public void setMeanTotalEnergy(double meanTotalEnergy) { this.meanTotalEnergy = meanTotalEnergy; }
    public double getEnergyDriftPerParticle() { return energyDriftPerParticle; }
    public void setEnergyDriftPerParticle(double energyDriftPerParticle) { this.energyDriftPerParticle = energyDriftPerParticle; }
}
