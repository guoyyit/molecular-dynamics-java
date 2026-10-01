package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointData;
import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointTest {
    @Test
    void writesAndReadsRoundTrip() throws Exception {
        Path directory = TestSupport.freshDirectory("md-cp-rt");
        try {
            CheckpointManager manager = new CheckpointManager(directory);
            CheckpointData original = sample(42, 123456789L);
            manager.write(original);

            CheckpointData loaded = manager.readLatest();
            assertNotNull(loaded);
            assertEquals(42, loaded.step);
            assertEquals(123456789L, loaded.rngState);
            assertArrayEquals(original.positions, loaded.positions);
            assertArrayEquals(original.velocities, loaded.velocities);
        } finally {
            TestSupport.deleteRecursively(directory);
        }
    }

    @Test
    void corruptNewestCheckpointFallsBackToOlder() throws Exception {
        Path directory = TestSupport.freshDirectory("md-cp-fb");
        try {
            CheckpointManager manager = new CheckpointManager(directory);
            CheckpointData older = sample(100, 111L);
            CheckpointData newer = sample(200, 222L);
            manager.write(older);
            Thread.sleep(20);
            manager.write(newer);

            List<Path> files;
            try (var stream = Files.list(directory)) {
                files = stream.filter(p -> p.toString().endsWith(".mdcp")).toList();
            }
            assertEquals(2, files.size());
            Path newest = files.get(0).toFile().lastModified()
                    >= files.get(1).toFile().lastModified() ? files.get(0) : files.get(1);
            Files.writeString(newest, "garbage-not-a-checkpoint", StandardCharsets.UTF_8);

            CheckpointData loaded = manager.readLatest();
            assertNotNull(loaded);
            assertEquals(100, loaded.step, "must fall back to the older valid checkpoint");
            assertEquals(111L, loaded.rngState);

            CheckpointData loadedAgain = manager.readLatest();
            assertNotNull(loadedAgain);
            assertEquals(100, loadedAgain.step);
        } finally {
            TestSupport.deleteRecursively(directory);
        }
    }

    @Test
    void noValidCheckpointReturnsNull() throws Exception {
        Path directory = TestSupport.freshDirectory("md-cp-none");
        try {
            CheckpointManager manager = new CheckpointManager(directory);
            manager.write(sample(1, 1L));
            try (var stream = Files.newDirectoryStream(directory, "*.mdcp")) {
                for (Path file : stream) {
                    Files.writeString(file, "MDCP1\nstep=x\nrng=y\n", StandardCharsets.UTF_8);
                }
            }
            assertNull(manager.readLatest());
        } finally {
            TestSupport.deleteRecursively(directory);
        }
    }

    @Test
    void noTempFilesLeftAfterWrite() throws Exception {
        Path directory = TestSupport.freshDirectory("md-cp-tmp");
        try {
            CheckpointManager manager = new CheckpointManager(directory);
            manager.write(sample(1, 1L));
            manager.write(sample(2, 2L));
            try (var stream = Files.list(directory)) {
                assertTrue(stream.noneMatch(p -> p.toString().endsWith(".tmp")));
            }
        } finally {
            TestSupport.deleteRecursively(directory);
        }
    }

    private static CheckpointData sample(long step, long rngState) {
        double[][] positions = {{0.1, 0.2, 0.3}, {1.5, -0.5, 2.25}};
        double[][] unwrapped = {{0.1, 0.2, 0.3}, {1.5, -0.5, 2.25}};
        double[][] velocities = {{-0.01, 0.02, 0.0}, {0.03, -0.04, 0.05}};
        return new CheckpointData(step, rngState, positions, unwrapped, velocities);
    }
}
