package com.github.guoyyit.moleculardynamicsjava.analysis;

import java.util.Arrays;

public class RadialDistributionFunction {
    private final int bins;
    private final double maxDistance;
    private final long[] histogram;
    private long frames;

    public RadialDistributionFunction(int bins, double maxDistance) {
        this.bins = bins;
        this.maxDistance = maxDistance;
        this.histogram = new long[bins];
    }

    public RadialDistributionFunction(int bins, double maxDistance, long[] histogram, long frames) {
        this.bins = bins;
        this.maxDistance = maxDistance;
        this.histogram = Arrays.copyOf(histogram, bins);
        this.frames = frames;
    }

    public void addFrame(double[] positions, double[] box, int n) {
        double cutoffSquared = maxDistance * maxDistance;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = positions[3 * i] - positions[3 * j];
                double dy = positions[3 * i + 1] - positions[3 * j + 1];
                double dz = positions[3 * i + 2] - positions[3 * j + 2];
                dx -= box[0] * Math.rint(dx / box[0]);
                dy -= box[1] * Math.rint(dy / box[1]);
                dz -= box[2] * Math.rint(dz / box[2]);
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < cutoffSquared) {
                    int bin = (int) (Math.sqrt(distanceSquared) / maxDistance * bins);
                    if (bin >= 0 && bin < bins) {
                        histogram[bin]++;
                    }
                }
            }
        }
        frames++;
    }

    public double[] finish(int n, double volume) {
        double[] g = new double[bins];
        if (frames == 0 || n < 2) {
            return g;
        }
        double numberDensity = (double) n / volume;
        double binWidth = maxDistance / bins;
        for (int k = 0; k < bins; k++) {
            double rInner = k * binWidth;
            double rOuter = rInner + binWidth;
            double shellVolume = (4.0 / 3.0) * Math.PI
                    * (rOuter * rOuter * rOuter - rInner * rInner * rInner);
            double expectedPairs = numberDensity * (double) n * frames * shellVolume;
            g[k] = expectedPairs == 0.0 ? 0.0 : histogram[k] / expectedPairs;
        }
        return g;
    }

    public long[] histogram() {
        return Arrays.copyOf(histogram, histogram.length);
    }

    public long frames() {
        return frames;
    }

    public double maxDistance() {
        return maxDistance;
    }
}
