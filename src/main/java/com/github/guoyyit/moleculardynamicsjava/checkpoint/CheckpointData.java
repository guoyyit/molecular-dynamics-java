package com.github.guoyyit.moleculardynamicsjava.checkpoint;

import java.util.Locale;

import com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample;

import java.util.ArrayList;
import java.util.List;

/**
 * Recoverable state of one simulation at a committed integration step.
 *
 * <p>The text format is line based and stable:
 * {@code MDCP1} magic, {@code step=<n>}, {@code rng=<state>}, then for each
 * particle a {@code p=<x>,<y>,<z>,<ux>,<uy>,<uz>,<vx>,<vy>,<vz>} line, and
 * finally a {@code crc32=<hex>} line covering all preceding bytes.
 */
public class CheckpointData {
    public long step;
    public long rngState;
    public double[][] positions;
    public double[][] unwrappedPositions;
    public double[][] velocities;
    public double[][] msdOrigin;
    public List<TrajectorySample> committedSamples = new ArrayList<>();
    public long rdfFrames;
    public long[] rdfHistogram = new long[0];
    public long[] msdLagSteps = new long[0];
    public double[] msdValues = new double[0];
    public int msdFilled;

    public CheckpointData() {
    }

    public CheckpointData(long step, long rngState, double[][] positions,
                          double[][] unwrappedPositions, double[][] velocities) {
        this.step = step;
        this.rngState = rngState;
        this.positions = positions;
        this.unwrappedPositions = unwrappedPositions;
        this.velocities = velocities;
    }

    /** Serialize without the checksum line; the manager appends the CRC. */
    public String toBody() {
        StringBuilder builder = new StringBuilder();
        builder.append("MDCP1\n");
        builder.append("step=").append(step).append('\n');
        builder.append("rng=").append(rngState).append('\n');
        builder.append("rdfframes=").append(rdfFrames).append('\n');
        builder.append("rdfbins=").append(rdfHistogram.length).append('\n');
        if (rdfHistogram.length > 0) {
            builder.append("h=");
            for (int b = 0; b < rdfHistogram.length; b++) {
                if (b > 0) {
                    builder.append(',');
                }
                builder.append(rdfHistogram[b]);
            }
            builder.append('\n');
        }
        builder.append("msdfilled=").append(msdFilled).append('\n');
        builder.append("msd=").append(msdLagSteps.length).append('\n');
        for (int k = 0; k < msdLagSteps.length; k++) {
            builder.append("m=").append(msdLagSteps[k]).append(',').append(num(msdValues[k]))
                    .append('\n');
        }
        builder.append("samples=").append(committedSamples.size()).append('\n');
        for (TrajectorySample sample : committedSamples) {
            builder.append("s=").append(sample.step)
                    .append(',').append(num(sample.time))
                    .append(',').append(num(sample.temperature))
                    .append(',').append(num(sample.potentialEnergy))
                    .append(',').append(num(sample.kineticEnergy))
                    .append(',').append(num(sample.totalEnergy)).append('\n');
        }
        builder.append("origin=").append(msdOrigin == null ? 0 : msdOrigin.length).append('\n');
        if (msdOrigin != null) {
            for (double[] origin : msdOrigin) {
                builder.append("o=").append(num(origin[0])).append(',')
                        .append(num(origin[1])).append(',')
                        .append(num(origin[2])).append('\n');
            }
        }
        for (int i = 0; i < positions.length; i++) {
            builder.append("p=")
                    .append(num(positions[i][0])).append(',')
                    .append(num(positions[i][1])).append(',')
                    .append(num(positions[i][2])).append(',')
                    .append(num(unwrappedPositions[i][0])).append(',')
                    .append(num(unwrappedPositions[i][1])).append(',')
                    .append(num(unwrappedPositions[i][2])).append(',')
                    .append(num(velocities[i][0])).append(',')
                    .append(num(velocities[i][1])).append(',')
                    .append(num(velocities[i][2])).append('\n');
        }
        return builder.toString();
    }

    static String num(double value) {
        if (value == 0.0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.17g", value);
    }
}
