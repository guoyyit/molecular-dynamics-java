package com.github.guoyyit.moleculardynamicsjava.stats;

import java.util.Locale;

/**
 * One sampled point of the temperature/energy time series.
 *
 * <p>The serialized CSV form is part of the stable output contract:
 * {@code step,time,temperature,potentialEnergy,kineticEnergy,totalEnergy}
 * with ASCII '.' decimal separators and full precision doubles.
 */
public class TrajectorySample {
    public long step;
    public double time;
    public double temperature;
    public double potentialEnergy;
    public double kineticEnergy;
    public double totalEnergy;

    public TrajectorySample() {
    }

    public TrajectorySample(long step, double time, double temperature,
                            double potentialEnergy, double kineticEnergy) {
        this.step = step;
        this.time = time;
        this.temperature = temperature;
        this.potentialEnergy = potentialEnergy;
        this.kineticEnergy = kineticEnergy;
        this.totalEnergy = potentialEnergy + kineticEnergy;
    }

    public static String csvHeader() {
        return "step,time,temperature,potentialEnergy,kineticEnergy,totalEnergy";
    }

    public String toCsvLine() {
        return String.format(Locale.ROOT, "%d,%.17g,%.17g,%.17g,%.17g,%.17g",
                step, time, temperature, potentialEnergy, kineticEnergy, totalEnergy);
    }
}
