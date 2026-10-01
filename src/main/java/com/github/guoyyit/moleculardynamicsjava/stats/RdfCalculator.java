package com.github.guoyyit.moleculardynamicsjava.stats;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.physics.PeriodicBox;

/**
 * Radial distribution function g(r) accumulated from minimum-image pair
 * distances, normalised by the ideal-gas shell volume.
 */
public class RdfCalculator {
    private static final int DEFAULT_BINS = 100;

    private final int particleCount;
    private final double maxDistance;
    private final int binCount;
    private final double binWidth;
    private final long[] histogram;
    private final PeriodicBox box;
    private long frames;

    private final double[] delta = new double[3];

    public RdfCalculator(SimulationConfig config) {
        particleCount = config.particles.size();
        box = new PeriodicBox(config.boxLength);
        double halfMin = 0.5 * StrictMath.min(config.boxLength[0],
                StrictMath.min(config.boxLength[1], config.boxLength[2]));
        maxDistance = StrictMath.min(config.cutoff + 0.5, halfMin);
        binCount = DEFAULT_BINS;
        binWidth = maxDistance / binCount;
        histogram = new long[binCount];
    }

    public int getBinCount() {
        return binCount;
    }

    public long getFrames() {
        return frames;
    }

    public long[] getHistogram() {
        return histogram.clone();
    }

    /** Restore histogram and frame count recorded in a checkpoint. */
    public void restore(long[] savedHistogram, long savedFrames) {
        if (savedHistogram.length != histogram.length) {
            throw new IllegalArgumentException("checkpoint RDF histogram does not match config");
        }
        System.arraycopy(savedHistogram, 0, histogram, 0, histogram.length);
        frames = savedFrames;
    }

    public double getBinWidth() {
        return binWidth;
    }

    public void accumulate(double[][] positions) {
        for (int i = 0; i < particleCount; i++) {
            for (int j = i + 1; j < particleCount; j++) {
                delta[0] = positions[i][0] - positions[j][0];
                delta[1] = positions[i][1] - positions[j][1];
                delta[2] = positions[i][2] - positions[j][2];
                box.minimumImage(delta);
                double r = StrictMath.sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
                int bin = (int) (r / binWidth);
                if (bin >= 0 && bin < binCount) {
                    histogram[bin] += 2L;
                }
            }
        }
        frames++;
    }

    /** Return g(r) sampled at the center of each bin. */
    public double[] finish() {
        double[] result = new double[binCount];
        if (frames == 0 || particleCount < 2) {
            return result;
        }
        double numberDensity = particleCount / box.volume();
        for (int b = 0; b < binCount; b++) {
            double rInner = b * binWidth;
            double rOuter = rInner + binWidth;
            double shellVolume = (4.0 / 3.0) * StrictMath.PI
                    * (rOuter * rOuter * rOuter - rInner * rInner * rInner);
            double idealPairs = numberDensity * shellVolume * particleCount * frames;
            result[b] = idealPairs > 0.0 ? histogram[b] / idealPairs : 0.0;
        }
        return result;
    }
}
