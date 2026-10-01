package com.github.guoyyit.moleculardynamicsjava.stats;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;

/**
 * Mean squared displacement measured from the initial frame (t = 0).
 *
 * <p>A fixed set of lag times is evaluated. Because every value is computed
 * purely from the t = 0 origin, resuming from a checkpoint reproduces the
 * exact same curve as an uninterrupted run.
 */
public class MsdCalculator {
    private final int particleCount;
    private final long[] lagSteps;
    private final double[] values;
    private double[][] origin;
    private int filled;

    public MsdCalculator(SimulationConfig config) {
        particleCount = config.particles.size();
        long totalSteps = StrictMath.max(1, StrictMath.round(config.duration / config.timeStep));
        lagSteps = new long[] {
                totalSteps / 4,
                totalSteps / 2,
                (3L * totalSteps) / 4,
                totalSteps
        };
        values = new double[lagSteps.length];
    }

    public long[] getLagSteps() {
        return lagSteps.clone();
    }

    public void setOrigin(double[][] unwrappedPositions) {
        origin = copy(unwrappedPositions);
    }

    public double[][] getOrigin() {
        return origin == null ? null : copy(origin);
    }

    public void restoreOrigin(double[][] savedOrigin) {
        origin = copy(savedOrigin);
    }

    /** Restore lag/value arrays recorded in a checkpoint. */
    public void restore(long[] savedLagSteps, double[] savedValues, int savedFilled) {
        if (savedLagSteps.length != lagSteps.length) {
            throw new IllegalArgumentException("checkpoint MSD lag table does not match config");
        }
        for (int k = 0; k < lagSteps.length; k++) {
            if (savedLagSteps[k] != lagSteps[k]) {
                throw new IllegalArgumentException("checkpoint MSD lag steps do not match config");
            }
            values[k] = savedValues[k];
        }
        filled = savedFilled;
    }

    public int getFilled() {
        return filled;
    }

    public double[] getValues() {
        return values.clone();
    }

    public void accumulate(long step, double[][] unwrappedPositions) {
        if (origin == null) {
            setOrigin(unwrappedPositions);
            return;
        }
        for (int k = filled; k < lagSteps.length; k++) {
            if (step != lagSteps[k]) {
                continue;
            }
            double total = 0.0;
            for (int i = 0; i < particleCount; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    double d = unwrappedPositions[i][axis] - origin[i][axis];
                    total += d * d;
                }
            }
            values[k] = total / particleCount;
            filled = k + 1;
        }
    }

    public double[] finish() {
        return values.clone();
    }

    private static double[][] copy(double[][] source) {
        double[][] target = new double[source.length][3];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, target[i], 0, 3);
        }
        return target;
    }
}
