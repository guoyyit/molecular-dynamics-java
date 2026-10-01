package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

final class TestSupport {
    private TestSupport() {
    }

    static SimulationEngine.RunControl neverStop() {
        return new SimulationEngine.RunControl() {
            @Override
            public boolean isCancelled(long completedSteps) {
                return false;
            }

            @Override
            public boolean isTimedOut(long completedSteps) {
                return false;
            }
        };
    }

    static SimulationResult runFresh(SimulationConfig config, Path directory) {
        SimulationEngine engine = new SimulationEngine(config, directory);
        return engine.run(neverStop());
    }

    static Path freshDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
