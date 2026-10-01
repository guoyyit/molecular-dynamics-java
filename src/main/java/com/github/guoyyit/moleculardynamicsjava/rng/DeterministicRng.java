package com.github.guoyyit.moleculardynamicsjava.rng;

public class DeterministicRng {
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;

    private long state;

    public DeterministicRng(long seed) {
        this.state = seed + GOLDEN_GAMMA;
    }

    public long getState() { return state; }
    public void setState(long state) { this.state = state; }

    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public double nextUniform() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double nextGaussian() {
        double u1;
        do {
            u1 = nextUniform();
        } while (u1 <= Double.MIN_NORMAL);
        double u2 = nextUniform();
        double magnitude = StrictMath.sqrt(-2.0 * StrictMath.log(u1));
        double angle = 2.0 * StrictMath.PI * u2;
        return magnitude * StrictMath.cos(angle);
    }
}
