package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.engine.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.Sample;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsConservationTest {
    private static final double MAX_ENERGY_DRIFT_PER_PARTICLE = 5.0e-4;

    @Test
    void nveTotalEnergyDriftIsBounded(@TempDir Path tempDir) {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        SimulationResult result = new SimulationEngine().run(config, tempDir.resolve("nve"),
                new AtomicBoolean(false));

        double first = result.getSamples().get(0).totalEnergy();
        double last = result.getSamples().get(result.getSamples().size() - 1).totalEnergy();
        double driftPerParticle = Math.abs(last - first) / result.getParticles();
        assertTrue(driftPerParticle <= MAX_ENERGY_DRIFT_PER_PARTICLE,
                "NVE 每粒子能量漂移 " + driftPerParticle
                        + " 超过上限 " + MAX_ENERGY_DRIFT_PER_PARTICLE);

        double maxAbsEnergy = result.getSamples().stream()
                .mapToDouble(Sample::totalEnergy).map(Math::abs).max().orElse(1.0);
        double relativeDrift = Math.abs(last - first) / maxAbsEnergy;
        assertTrue(relativeDrift < 2.0e-4,
                "NVE 相对能量漂移 " + relativeDrift + " 过大");
    }

    @Test
    void nvtTemperatureConvergesWithControlledFluctuations(@TempDir Path tempDir) {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nvt");
        double target = config.getTargetTemperature();
        SimulationResult result = new SimulationEngine().run(config, tempDir.resolve("nvt"),
                new AtomicBoolean(false));

        int sampleCount = result.getSamples().size();
        int fromIndex = sampleCount / 2;
        double mean = result.getSamples().subList(fromIndex, sampleCount).stream()
                .mapToDouble(Sample::temperature).average().orElse(0.0);
        double variance = result.getSamples().subList(fromIndex, sampleCount).stream()
                .mapToDouble(sample -> {
                    double delta = sample.temperature() - mean;
                    return delta * delta;
                }).average().orElse(0.0);
        double relativeRms = Math.sqrt(variance) / target;

        assertTrue(Math.abs(mean - target) / target < 0.15,
                "NVT 后半段平均温度 " + mean + " 偏离目标 " + target);
        assertTrue(relativeRms < 0.25,
                "NVT 温度相对涨落 " + relativeRms + " 超过受控上限 0.25");
    }

    @Test
    void builtInExampleMatchesReferenceStatistics(@TempDir Path tempDir) {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        SimulationResult result = new SimulationEngine().run(config,
                tempDir.resolve("reference"), new AtomicBoolean(false));

        assertTrue(result.getSamples().size() == 101,
                "示例采样点数固定为 101, 实际: " + result.getSamples().size());
        assertReference(result.getMeanTotalEnergy(), -412.185, 0.02);
        assertReference(result.getMeanTemperature(), 0.750, 0.03);

        double peak = 0.0;
        for (double value : result.getRdfValues()) {
            peak = Math.max(peak, value);
        }
        assertTrue(peak > 1.5 && peak < 3.5, "内置示例 RDF 第一峰 " + peak + " 不在参考区间");

        double[] msd = result.getMsdValues();
        assertTrue(msd.length > 10, "MSD 时间序列过短");
        assertTrue(msd[msd.length - 1] > msd[0], "扩散体系 MSD 应随时间增长");
    }

    private void assertReference(double actual, double expected, double relativeTolerance) {
        assertTrue(Math.abs(actual - expected) <= relativeTolerance * Math.abs(expected),
                "参考统计值 " + actual + " 与预期 " + expected + " 的偏差超过容差 "
                        + relativeTolerance);
    }
}
