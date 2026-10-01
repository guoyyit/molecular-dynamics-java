package com.github.guoyyit.moleculardynamicsjava.model;

import java.util.ArrayList;
import java.util.List;

public class SimulationState {
    public long step;
    public double time;
    public int n;
    public double[] masses = new double[0];
    public double[] pos = new double[0];
    public double[] vel = new double[0];
    double[] force = new double[0];
    public double[] unwrapped = new double[0];
    public double[] msdReference = new double[0];
    public long rngState;
    public List<Sample> samples = new ArrayList<>();
    public long[] rdfHistogram = new long[0];
    public long rdfFrames;
    public long[] msdSteps = new long[0];
    public double[] msdTimes = new double[0];
    public double[] msdValues = new double[0];

    public SimulationState() {
    }

    public double[] getForce() { return force; }
    public void setForce(double[] force) { this.force = force; }
}
