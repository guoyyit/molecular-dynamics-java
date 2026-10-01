package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointStore;
import com.github.guoyyit.moleculardynamicsjava.engine.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.Sample;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointRecoveryTest {
    private static AtomicBoolean delayedCancel(long delayMillis) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            cancelled.set(true);
        });
        thread.setDaemon(true);
        thread.start();
        return cancelled;
    }

    @Test
    void resumedRunMatchesUninterruptedRun(@TempDir Path tempDir) {
        SimulationConfig fullConfig = ExampleSystems.all().get("argon-fcc-nve");
        fullConfig.setCheckpointInterval(200L);
        SimulationResult uninterrupted = new SimulationEngine().run(fullConfig,
                tempDir.resolve("full"), new AtomicBoolean(false));

        SimulationConfig partialConfig = ExampleSystems.all().get("argon-fcc-nve");
        partialConfig.setCheckpointInterval(200L);
        Path partialDirectory = tempDir.resolve("partial");
        AtomicBoolean cancelled = delayedCancel(80);
        SimulationEngine engine = new SimulationEngine();
        SimulationResult firstSegment = engine.run(partialConfig, partialDirectory, cancelled);
        assertTrue(firstSegment.getSteps() >= 200 && firstSegment.getSteps() < fullConfig.getSteps(),
                "取消的运行应在完成前停止");

        SimulationResult resumed = engine.resume(partialDirectory,
                fullConfig.getSteps() - firstSegment.getSteps(), new AtomicBoolean(false));
        assertEquals(fullConfig.getSteps(), resumed.getSteps());
        assertEquals(uninterrupted.getSamples().size(), resumed.getSamples().size(),
                "续跑后的采样点数应与不中断运行一致");

        List<Sample> expected = uninterrupted.getSamples();
        List<Sample> actual = resumed.getSamples();
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(Double.doubleToLongBits(expected.get(i).totalEnergy()),
                    Double.doubleToLongBits(actual.get(i).totalEnergy()),
                    "第 " + i + " 个采样点总能量不一致");
            assertEquals(Double.doubleToLongBits(expected.get(i).temperature()),
                    Double.doubleToLongBits(actual.get(i).temperature()));
        }
    }

    @Test
    void corruptedLatestCheckpointFallsBackToEarlier(@TempDir Path tempDir) throws Exception {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setCheckpointInterval(200L);
        Path directory = tempDir.resolve("job");
        AtomicBoolean cancelled = delayedCancel(80);
        SimulationEngine engine = new SimulationEngine();
        engine.run(config, directory, cancelled);

        CheckpointStore store = new CheckpointStore(directory.resolve("checkpoints"),
                config.getRetainedCheckpoints());
        List<Path> checkpoints;
        try (var stream = Files.list(directory.resolve("checkpoints"))) {
            checkpoints = stream.filter(path -> path.getFileName().toString()
                    .startsWith("checkpoint-")).sorted().toList();
        }
        assertTrue(checkpoints.size() >= 2, "应至少保留两个检查点用于回退验证");

        Path latest = checkpoints.get(checkpoints.size() - 1);
        Files.write(latest, "broken-json-content".getBytes(StandardCharsets.UTF_8));

        CheckpointStore.Checkpoint recovered = store.latest();
        assertNotNull(recovered, "最新检查点损坏时必须回退到更早的有效检查点");
        assertEquals(checkpoints.get(checkpoints.size() - 2), recovered.file);
    }
}
