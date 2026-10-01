package com.github.guoyyit.moleculardynamicsjava.rng;

/**
 * Deterministic, self-contained pseudo random generator.
 *
 * <p>Based on SplitMix64 (the same mixer used by Java's SplittableRandom).
 * The sequence depends only on the seed, so identical seeds always produce
 * identical streams on every platform/JVM. Gaussian samples are generated
 * with the classic Box-Muller transform using {@link StrictMath} only.
 */
public class DeterministicRandom {
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;

    private long state;
    private boolean hasSpareGaussian;
    private double spareGaussian;

    public DeterministicRandom(long seed) {
        this.state = seed;
    }

    public long getState() {
        return state;
    }

    /** Restore the internal state; the cached Box-Muller spare is discarded. */
    public void setState(long newState) {
        this.state = newState;
        hasSpareGaussian = false;
    }

    private long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** Uniform double in [0, 1) with 53 bits of mantissa. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Standard normal sample, Box-Muller transform, bit deterministic via StrictMath. */
    public double nextGaussian() {
        if (hasSpareGaussian) {
            hasSpareGaussian = false;
            return spareGaussian;
        }
        double u1 = nextDouble();
        if (u1 == 0.0) {
            u1 = Double.MIN_VALUE;
        }
        double u2 = nextDouble();
        double radius = StrictMath.sqrt(-2.0 * StrictMath.log(u1));
        double angle = 2.0 * StrictMath.PI * u2;
        spareGaussian = radius * StrictMath.sin(angle);
        hasSpareGaussian = true;
        return radius * StrictMath.cos(angle);
    }
}
