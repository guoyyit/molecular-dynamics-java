package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.config.ConfigValidator;
import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.ParticleSpec;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidationTest {
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void validExamplePasses() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        assertTrue(validator.validate(config).isEmpty(),
                "内置示例应当合法: " + validator.validate(config));
    }

    @Test
    void rejectsNegativeMass() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setSpecies(List.of(new com.github.guoyyit.moleculardynamicsjava.model.Species(
                "Ar", -1.0)));
        List<String> errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(error -> error.contains("质量")),
                "必须拒绝负质量, 实际: " + errors);
    }

    @Test
    void rejectsOutOfBoundsPosition() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        double[] outOfBounds = {100.0, 0.0, 0.0};
        config.getParticles().set(0,
                new ParticleSpec("Ar", outOfBounds, new double[]{0.0, 0.0, 0.0}));
        List<String> errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(error -> error.contains("越界")),
                "必须拒绝越界位置, 实际: " + errors);
    }

    @Test
    void rejectsOverlappingParticles() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        double[] first = config.getParticles().get(0).position();
        config.getParticles().set(1,
                new ParticleSpec("Ar", first.clone(), new double[]{0.0, 0.0, 0.0}));
        List<String> errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(error -> error.contains("最小镜像距离")),
                "必须拒绝过度重叠的粒子, 实际: " + errors);
    }

    @Test
    void rejectsCutoffLargerThanHalfBox() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setCutoff(3.0);
        List<String> errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(error -> error.contains("最小镜像")),
                "必须拒绝大于盒长一半的截断半径, 实际: " + errors);
    }

    @Test
    void rejectsMissingTargetTemperatureInNvt() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nvt");
        config.setTargetTemperature(null);
        List<String> errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(error -> error.contains("targetTemperature")),
                "NVT 缺少目标温度必须被拒绝, 实际: " + errors);
    }

    @Test
    void rejectsNonPositiveTimeStep() {
        SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
        config.setTimeStep(0.0);
        List<String> errors = validator.validate(config);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("时间步长")));
    }
}
