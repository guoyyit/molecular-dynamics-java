package com.github.guoyyit.moleculardynamicsjava.physics;

/**
 * Orthorhombic periodic simulation box with minimum-image convention.
 */
public class PeriodicBox {
    private final double lx;
    private final double ly;
    private final double lz;

    public PeriodicBox(double[] length) {
        this.lx = length[0];
        this.ly = length[1];
        this.lz = length[2];
    }

    /** Wrap a position vector in place into the half-open box [0, L). */
    public void wrap(double[] position) {
        position[0] -= lx * StrictMath.floor(position[0] / lx);
        position[1] -= ly * StrictMath.floor(position[1] / ly);
        position[2] -= lz * StrictMath.floor(position[2] / lz);
    }

    /** Wrap every particle position in place. */
    public void wrapAll(double[][] positions) {
        for (double[] position : positions) {
            wrap(position);
        }
    }

    /** Reduce a displacement vector in place to the minimum image. */
    public void minimumImage(double[] delta) {
        delta[0] -= lx * StrictMath.rint(delta[0] / lx);
        delta[1] -= ly * StrictMath.rint(delta[1] / ly);
        delta[2] -= lz * StrictMath.rint(delta[2] / lz);
    }

    public double length(int axis) {
        return switch (axis) {
            case 0 -> lx;
            case 1 -> ly;
            default -> lz;
        };
    }

    public double volume() {
        return lx * ly * lz;
    }
}
