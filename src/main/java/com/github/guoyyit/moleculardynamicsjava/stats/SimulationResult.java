package com.github.guoyyit.moleculardynamicsjava.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Complete output of one simulation run: time series, RDF and MSD.
 */
public class SimulationResult {
    public String jobId;
    public String ensemble;
    public long steps;
    public double duration;
    public double targetTemperature;
    public String terminationReason;
    public long resumedFromStep;
    public List<TrajectorySample> samples = new ArrayList<>();
    public double[] rdfBinEdges = new double[0];
    public double[] rdfValues = new double[0];
    public double rdfBinWidth;
    public double[] msdTimes = new double[0];
    public double[] msdValues = new double[0];

    public SimulationResult() {
    }

    /** Stable, parseable representation of the temperature/energy time series. */
    public String toTimeSeriesCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append(TrajectorySample.csvHeader()).append('\n');
        for (TrajectorySample sample : samples) {
            builder.append(sample.toCsvLine()).append('\n');
        }
        return builder.toString();
    }

    /** Stable, parseable representation of g(r). */
    public String toRdfCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("binCenter,rdf\n");
        for (int i = 0; i < rdfValues.length; i++) {
            builder.append(String.format(Locale.ROOT, "%.17g,%.17g%n", rdfBinEdges[i], rdfValues[i]));
        }
        return builder.toString();
    }

    /** Stable, parseable representation of the MSD curve. */
    public String toMsdCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("time,msd\n");
        for (int i = 0; i < msdValues.length; i++) {
            builder.append(String.format(Locale.ROOT, "%.17g,%.17g%n", msdTimes[i], msdValues[i]));
        }
        return builder.toString();
    }
}
