package com.github.guoyyit.moleculardynamicsjava.checkpoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;

/**
 * File based, crash-safe checkpoint storage.
 *
 * <p>Writes are atomic (temp file + ATOMIC_MOVE where supported, otherwise
 * a fallback move) and each file ends with a CRC32 line. Two checkpoint
 * slots are kept; when reading, the newest valid file wins and corrupted
 * files are deleted so recovery automatically falls back to the previous
 * valid checkpoint.
 */
public class CheckpointManager {
    private static final int SLOTS = 2;

    private final Path directory;
    private int writeRoundRobin;

    public CheckpointManager(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create checkpoint directory " + directory, e);
        }
    }

    public void write(CheckpointData data) {
        Path target = directory.resolve("checkpoint-" + writeRoundRobin + ".mdcp");
        writeRoundRobin = (writeRoundRobin + 1) % SLOTS;
        Path temp = directory.resolve("checkpoint-" + writeRoundRobin + "-"
                + System.nanoTime() + ".tmp");
        String body = data.toBody();
        CRC32 crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        String content = body + String.format("crc32=%08x%n", crc.getValue());
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Failed to write checkpoint " + target, e);
        }
    }

    /**
     * Read the newest valid checkpoint, or return {@code null} if none is
     * valid. Corrupted files are removed so the next call sees the fallback.
     */
    public CheckpointData readLatest() {
        List<Path> files = listCheckpointFiles();
        for (Path file : files) {
            CheckpointData data = tryRead(file);
            if (data != null) {
                return data;
            }
            deleteQuietly(file);
        }
        return null;
    }

    private List<Path> listCheckpointFiles() {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(directory, "checkpoint-*.mdcp")) {
            for (Path path : stream) {
                files.add(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list checkpoints in " + directory, e);
        }
        files.sort(Comparator.comparingLong(this::lastModifiedQuietly).reversed());
        return files;
    }

    private long lastModifiedQuietly(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private CheckpointData tryRead(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
        if (lines.size() < 4 || !"MDCP1".equals(lines.get(0))) {
            return null;
        }
        String lastLine = lines.get(lines.size() - 1);
        if (!lastLine.startsWith("crc32=")) {
            return null;
        }
        long expected;
        try {
            expected = Long.parseLong(lastLine.substring("crc32=".length()).trim(), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < lines.size() - 1; i++) {
            body.append(lines.get(i)).append('\n');
        }
        CRC32 crc = new CRC32();
        crc.update(body.toString().getBytes(StandardCharsets.UTF_8));
        if (crc.getValue() != expected) {
            return null;
        }
        try {
            return parse(lines);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private CheckpointData parse(List<String> lines) {
        CheckpointData data = new CheckpointData();
        data.step = Long.parseLong(valueAfter(lines.get(1), "step="));
        data.rngState = Long.parseLong(valueAfter(lines.get(2), "rng="));
        int index = 3;
        data.rdfFrames = Long.parseLong(valueAfter(lines.get(index++), "rdfframes="));
        int rdfBinCount = Integer.parseInt(valueAfter(lines.get(index++), "rdfbins="));
        long[] rdfHistogram = new long[rdfBinCount];
        if (rdfBinCount > 0) {
            String[] histogramParts = valueAfter(lines.get(index++), "h=").split(",");
            if (histogramParts.length != rdfBinCount) {
                throw new IllegalArgumentException("Bad histogram length");
            }
            for (int b = 0; b < rdfBinCount; b++) {
                rdfHistogram[b] = Long.parseLong(histogramParts[b]);
            }
        }
        data.rdfHistogram = rdfHistogram;
        data.msdFilled = Integer.parseInt(valueAfter(lines.get(index++), "msdfilled="));
        int msdCount = Integer.parseInt(valueAfter(lines.get(index++), "msd="));
        data.msdLagSteps = new long[msdCount];
        data.msdValues = new double[msdCount];
        for (int k = 0; k < msdCount; k++) {
            String[] msdParts = valueAfter(lines.get(index++), "m=").split(",");
            if (msdParts.length != 2) {
                throw new IllegalArgumentException("Bad msd line");
            }
            data.msdLagSteps[k] = Long.parseLong(msdParts[0]);
            data.msdValues[k] = Double.parseDouble(msdParts[1]);
        }
        int sampleCount = Integer.parseInt(valueAfter(lines.get(index++), "samples="));
        List<com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample> samples =
                new ArrayList<>();
        for (int s = 0; s < sampleCount; s++) {
            String[] parts = valueAfter(lines.get(index++), "s=").split(",");
            if (parts.length != 6) {
                throw new IllegalArgumentException("Bad sample line");
            }
            com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample sample =
                    new com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample();
            sample.step = Long.parseLong(parts[0]);
            sample.time = Double.parseDouble(parts[1]);
            sample.temperature = Double.parseDouble(parts[2]);
            sample.potentialEnergy = Double.parseDouble(parts[3]);
            sample.kineticEnergy = Double.parseDouble(parts[4]);
            sample.totalEnergy = Double.parseDouble(parts[5]);
            samples.add(sample);
        }
        data.committedSamples = samples;
        int originCount = Integer.parseInt(valueAfter(lines.get(index++), "origin="));
        List<double[]> originList = new ArrayList<>();
        for (int o = 0; o < originCount; o++) {
            String[] parts = valueAfter(lines.get(index++), "o=").split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Bad origin line");
            }
            originList.add(new double[] {
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])
            });
        }
        data.msdOrigin = originList.toArray(new double[0][]);
        List<double[]> positionList = new ArrayList<>();
        List<double[]> unwrappedList = new ArrayList<>();
        List<double[]> velocityList = new ArrayList<>();
        for (; index < lines.size() - 1; index++) {
            String line = lines.get(index);
            if (!line.startsWith("p=")) {
                throw new IllegalArgumentException("Bad particle line: " + line);
            }
            String[] numbers = line.substring(2).split(",");
            if (numbers.length != 9) {
                throw new IllegalArgumentException("Bad particle line: " + line);
            }
            double[] position = new double[3];
            double[] unwrapped = new double[3];
            double[] velocity = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                position[axis] = Double.parseDouble(numbers[axis]);
                unwrapped[axis] = Double.parseDouble(numbers[axis + 3]);
                velocity[axis] = Double.parseDouble(numbers[axis + 6]);
            }
            positionList.add(position);
            unwrappedList.add(unwrapped);
            velocityList.add(velocity);
        }
        data.positions = positionList.toArray(new double[0][]);
        data.unwrappedPositions = unwrappedList.toArray(new double[0][]);
        data.velocities = velocityList.toArray(new double[0][]);
        if (data.positions.length == 0) {
            throw new IllegalArgumentException("Checkpoint contains no particles");
        }
        return data;
    }

    private static String valueAfter(String line, String prefix) {
        if (!line.startsWith(prefix)) {
            throw new IllegalArgumentException("Expected " + prefix + " but got " + line);
        }
        return line.substring(prefix.length());
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; stale files are harmless and retried later.
        }
    }
}
