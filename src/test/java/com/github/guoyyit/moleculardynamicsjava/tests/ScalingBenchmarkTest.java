package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.engine.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")
class ScalingBenchmarkTest {
    @Test
    void stepTimeScalesLinearlyWithParticleCount(@TempDir Path tempDir) {
        Benchmark small = measure(5, tempDir.resolve("n500"));
        Benchmark medium = measure(7, tempDir.resolve("n1372"));
        Benchmark large = measure(8, tempDir.resolve("n2048"));

        double particleRatio = (double) large.particles / medium.particles;
        double timeRatio = large.millisPerStep / medium.millisPerStep;
        double exponent = Math.log(timeRatio) / Math.log(particleRatio);
        System.out.printf("Scaling: N=%d %.4f ms/step, N=%d %.4f ms/step, N=%d %.4f ms/step, "
                        + "ratio=%.2f over %.2fx particles, exponent=%.2f%n",
                small.particles, small.millisPerStep,
                medium.particles, medium.millisPerStep,
                large.particles, large.millisPerStep,
                timeRatio, particleRatio, exponent);

        assertTrue(exponent < 1.3,
                "单步耗时增长 " + timeRatio + " 倍, 粒子数增长 " + particleRatio
                        + " 倍, 复杂度指数 " + exponent + " 不满足近似线性要求 (元胞列表路径)");
    }

    private Benchmark measure(int cells, Path directory) {
        SimulationConfig config = ExampleSystems.argonFcc(cells,
                com.github.guoyyit.moleculardynamicsjava.model.Ensemble.NVE, 300L, 1L);
        config.setSampleInterval(10_000L);
        config.setCheckpointInterval(1000L);
        SimulationEngine engine = new SimulationEngine();
        config.setSteps(100);
        engine.run(config, directory.resolve("warmup"), new AtomicBoolean(false));
        long start = System.nanoTime();
        config.setSteps(200);
        SimulationResult result = engine.run(config, directory.resolve("timed"),
                new AtomicBoolean(false));
        double elapsedMillis = (System.nanoTime() - start) / 1.0e6;
        Benchmark benchmark = new Benchmark();
        benchmark.particles = result.getParticles();
        benchmark.millisPerStep = elapsedMillis / result.getSteps();
        return benchmark;
    }

    private static class Benchmark {
        int particles;
        double millisPerStep;
    }
}
