package com.github.guoyyit.moleculardynamicsjava.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.moleculardynamicsjava.model.Sample;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class ResultWriter {
    private static final String CSV_HEADER_SAMPLES =
            "step,time,temperature,kinetic_energy,potential_energy,total_energy";

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Path outputDirectory, SimulationResult result) throws IOException {
        Files.createDirectories(outputDirectory);
        writeSamples(outputDirectory, result);
        writeRdf(outputDirectory, result);
        writeMsd(outputDirectory, result);
        writeSummary(outputDirectory, result);
    }

    private void writeSamples(Path directory, SimulationResult result) throws IOException {
        StringBuilder builder = new StringBuilder(CSV_HEADER_SAMPLES).append('\n');
        for (Sample sample : result.getSamples()) {
            builder.append(sample.step()).append(',')
                    .append(format(sample.time())).append(',')
                    .append(format(sample.temperature())).append(',')
                    .append(format(sample.kineticEnergy())).append(',')
                    .append(format(sample.potentialEnergy())).append(',')
                    .append(format(sample.totalEnergy())).append('\n');
        }
        atomicallyWrite(directory.resolve("samples.csv"), builder.toString());
    }

    private void writeRdf(Path directory, SimulationResult result) throws IOException {
        StringBuilder builder = new StringBuilder("r,g(r)\n");
        double[] edges = result.getRdfEdges();
        double[] values = result.getRdfValues();
        for (int i = 0; i < edges.length; i++) {
            builder.append(format(edges[i])).append(',')
                    .append(format(values[i])).append('\n');
        }
        atomicallyWrite(directory.resolve("rdf.csv"), builder.toString());
    }

    private void writeMsd(Path directory, SimulationResult result) throws IOException {
        StringBuilder builder = new StringBuilder("lag_time,msd\n");
        double[] times = result.getMsdTimes();
        double[] values = result.getMsdValues();
        for (int i = 0; i < times.length; i++) {
            builder.append(format(times[i])).append(',')
                    .append(format(values[i])).append('\n');
        }
        atomicallyWrite(directory.resolve("msd.csv"), builder.toString());
    }

    private void writeSummary(Path directory, SimulationResult result) throws IOException {
        Path target = directory.resolve("result.json");
        Path temporary = directory.resolve(".result." + System.nanoTime() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), result);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void atomicallyWrite(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling("." + target.getFileName() + "."
                + System.nanoTime() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }
}
