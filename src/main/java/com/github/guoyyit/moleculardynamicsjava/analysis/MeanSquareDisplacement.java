package com.github.guoyyit.moleculardynamicsjava.analysis;

import java.util.ArrayList;
import java.util.List;

public class MeanSquareDisplacement {
    private final int maxLags;
    private double[] reference;
    private int particleCount;
    private final List<Long> lagSteps = new ArrayList<>();
    private final List<Double> lagTimes = new ArrayList<>();
    private final List<Double> displacements = new ArrayList<>();

    public MeanSquareDisplacement() {
        this(100);
    }

    public MeanSquareDisplacement(int maxLags) {
        this.maxLags = maxLags;
    }

    public void restore(double[] reference, int n, long[] steps, double[] times, double[] values) {
        this.particleCount = n;
        this.reference = reference.clone();
        for (int i = 0; i < steps.length; i++) {
            lagSteps.add(steps[i]);
            lagTimes.add(times[i]);
            displacements.add(values[i]);
        }
    }

    public void setReference(double[] unwrapped, int n) {
        this.particleCount = n;
        this.reference = unwrapped.clone();
        lagSteps.clear();
        lagTimes.clear();
        displacements.clear();
        addFrame(0L, 0.0, unwrapped, n);
    }

    public void addFrame(long step, double time, double[] unwrapped, int n) {
        if (reference == null) {
            setReference(unwrapped, n);
            return;
        }
        if (displacements.size() >= maxLags) {
            return;
        }
        double sum = 0.0;
        for (int i = 0; i < 3 * n; i++) {
            double delta = unwrapped[i] - reference[i];
            sum += delta * delta;
        }
        lagSteps.add(step);
        lagTimes.add(time);
        displacements.add(sum / n);
    }

    public double[] times() {
        return lagTimes.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public double[] values() {
        return displacements.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public long[] steps() {
        return lagSteps.stream().mapToLong(Long::longValue).toArray();
    }

    public double[] reference() {
        return reference == null ? null : reference.clone();
    }

    public int particleCount() {
        return particleCount;
    }
}
