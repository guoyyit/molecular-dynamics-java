package com.github.guoyyit.moleculardynamicsjava.config;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Ensemble;
import com.github.guoyyit.moleculardynamicsjava.model.ParticleSpec;
import com.github.guoyyit.moleculardynamicsjava.model.Species;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigValidator {
    private static final double POSITION_TOLERANCE = 1.0e-9;

    public List<String> validate(SimulationConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("配置不能为空 (config must not be null)");
            return errors;
        }
        validateBox(config, errors);
        validateSpecies(config, errors);
        validateParticles(config, errors);
        validatePotential(config, errors);
        validateRun(config, errors);
        validateOutput(config, errors);
        return errors;
    }

    private void validateBox(SimulationConfig config, List<String> errors) {
        double[] box = config.getBox();
        if (box == null || box.length != 3) {
            errors.add("盒子尺寸 box 必须是长度为 3 的数组 [Lx, Ly, Lz]");
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (!Double.isFinite(box[i]) || box[i] <= 0.0) {
                errors.add("盒子边长 box[" + i + "] 必须为正数, 实际值: " + box[i]);
            }
        }
    }

    private void validateSpecies(SimulationConfig config, List<String> errors) {
        List<Species> speciesList = config.getSpecies();
        if (speciesList == null || speciesList.isEmpty()) {
            errors.add("至少需要定义一种粒子种类 species");
            return;
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < speciesList.size(); i++) {
            Species species = speciesList.get(i);
            if (species == null || species.name() == null || species.name().isBlank()) {
                errors.add("species[" + i + "] 缺少名称");
                continue;
            }
            if (!names.add(species.name())) {
                errors.add("粒子种类名称重复: " + species.name());
            }
            if (!Double.isFinite(species.mass()) || species.mass() <= 0.0) {
                errors.add("粒子种类 " + species.name() + " 的质量必须为正数, 实际值: " + species.mass());
            }
        }
    }

    private void validateParticles(SimulationConfig config, List<String> errors) {
        List<ParticleSpec> particles = config.getParticles();
        if (particles == null || particles.isEmpty()) {
            errors.add("至少需要一个粒子 particles");
            return;
        }
        Set<String> speciesNames = new HashSet<>();
        for (Species species : config.getSpecies()) {
            speciesNames.add(species.name());
        }
        double[] box = config.getBox();
        for (int i = 0; i < particles.size(); i++) {
            ParticleSpec particle = particles.get(i);
            if (particle == null) {
                errors.add("particles[" + i + "] 为空");
                continue;
            }
            if (!speciesNames.contains(particle.species())) {
                errors.add("particles[" + i + "] 引用了未定义的粒子种类: " + particle.species());
            }
            double[] position = particle.position();
            if (position == null || position.length != 3) {
                errors.add("particles[" + i + "].position 必须是长度为 3 的数组");
            } else {
                for (int axis = 0; axis < 3; axis++) {
                    double value = position[axis];
                    if (!Double.isFinite(value)) {
                        errors.add("particles[" + i + "].position[" + axis + "] 不是有限数值");
                    } else if (box != null && box.length == 3
                            && (value < -POSITION_TOLERANCE || value >= box[axis] + POSITION_TOLERANCE)) {
                        errors.add("particles[" + i + "].position[" + axis + "] = " + value
                                + " 越界, 必须落在半开区间 [0, " + box[axis] + ") 内");
                    }
                }
            }
            double[] velocity = particle.velocity();
            if (velocity != null) {
                if (velocity.length != 3) {
                    errors.add("particles[" + i + "].velocity 必须是长度为 3 的数组");
                } else {
                    for (int axis = 0; axis < 3; axis++) {
                        if (!Double.isFinite(velocity[axis])) {
                            errors.add("particles[" + i + "].velocity[" + axis + "] 不是有限数值");
                        }
                    }
                }
            }
        }
        validateOverlap(config, errors);
    }

    private void validateOverlap(SimulationConfig config, List<String> errors) {
        List<ParticleSpec> particles = config.getParticles();
        double[] box = config.getBox();
        if (particles == null || box == null || box.length != 3) {
            return;
        }
        boolean boxValid = true;
        for (double edge : box) {
            if (!Double.isFinite(edge) || edge <= 0.0) {
                boxValid = false;
            }
        }
        if (!boxValid) {
            return;
        }
        double threshold = config.getMinSeparation() * config.getSigma();
        double thresholdSquared = threshold * threshold;
        int n = particles.size();
        for (int i = 0; i < n; i++) {
            double[] pi = particles.get(i) == null ? null : particles.get(i).position();
            if (pi == null || pi.length != 3) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                double[] pj = particles.get(j) == null ? null : particles.get(j).position();
                if (pj == null || pj.length != 3) {
                    continue;
                }
                double distanceSquared = 0.0;
                for (int axis = 0; axis < 3; axis++) {
                    double delta = pi[axis] - pj[axis];
                    delta -= box[axis] * Math.rint(delta / box[axis]);
                    distanceSquared += delta * delta;
                }
                if (distanceSquared + 1.0e-12 < thresholdSquared) {
                    errors.add("粒子 " + i + " 与 " + j + " 最小镜像距离 "
                            + String.format(java.util.Locale.ROOT, "%.4f", Math.sqrt(distanceSquared))
                            + " 小于重叠下限 " + String.format(java.util.Locale.ROOT, "%.4f", threshold)
                            + " (minSeparation * sigma)");
                    return;
                }
            }
        }
    }

    private void validatePotential(SimulationConfig config, List<String> errors) {
        if (!Double.isFinite(config.getEpsilon()) || config.getEpsilon() <= 0.0) {
            errors.add("势阱深度 epsilon 必须为正数, 实际值: " + config.getEpsilon());
        }
        if (!Double.isFinite(config.getSigma()) || config.getSigma() <= 0.0) {
            errors.add("碰撞直径 sigma 必须为正数, 实际值: " + config.getSigma());
        }
        if (!Double.isFinite(config.getCutoff()) || config.getCutoff() <= 0.0) {
            errors.add("截断半径 cutoff 必须为正数, 实际值: " + config.getCutoff());
        } else {
            double[] box = config.getBox();
            if (box != null && box.length == 3) {
                double minEdge = Math.min(box[0], Math.min(box[1], box[2]));
                if (config.getCutoff() >= minEdge / 2.0) {
                    errors.add("截断半径 cutoff (" + config.getCutoff()
                            + ") 必须小于盒子最短边长的一半 (" + minEdge / 2.0
                            + "), 否则最小镜像约定不成立");
                }
            }
        }
        if (!Double.isFinite(config.getMinSeparation()) || config.getMinSeparation() <= 0.0) {
            errors.add("重叠下限 minSeparation 必须为正数, 实际值: " + config.getMinSeparation());
        }
    }

    private void validateRun(SimulationConfig config, List<String> errors) {
        if (config.getEnsemble() == null) {
            errors.add("系综 ensemble 必须为 NVE 或 NVT");
        }
        if (!Double.isFinite(config.getTimeStep()) || config.getTimeStep() <= 0.0) {
            errors.add("时间步长 timeStep 必须为正数, 实际值: " + config.getTimeStep());
        }
        if (config.getSteps() < 1) {
            errors.add("运行步数 steps 必须 >= 1, 实际值: " + config.getSteps());
        }
        if (config.getEnsemble() == Ensemble.NVT) {
            Double target = config.getTargetTemperature();
            if (target == null || !Double.isFinite(target) || target <= 0.0) {
                errors.add("NVT 模式必须指定正数 targetTemperature");
            }
            if (!Double.isFinite(config.getThermostatFriction()) || config.getThermostatFriction() < 0.0) {
                errors.add("thermostatFriction 必须为非负数, 实际值: " + config.getThermostatFriction());
            }
        }
        if (config.getMaxTemperature() != null
                && (!Double.isFinite(config.getMaxTemperature()) || config.getMaxTemperature() <= 0.0)) {
            errors.add("maxTemperature 必须为正数或 null");
        }
        if (!Double.isFinite(config.getMaxUnwrappedDistance()) || config.getMaxUnwrappedDistance() <= 0.0) {
            errors.add("maxUnwrappedDistance 必须为正数");
        }
        if (config.getTimeoutMillis() < 0) {
            errors.add("timeoutMillis 必须为非负数, 0 表示不限时");
        }
    }

    private void validateOutput(SimulationConfig config, List<String> errors) {
        if (config.getSampleInterval() < 1) {
            errors.add("sampleInterval 必须 >= 1");
        }
        if (config.getCheckpointInterval() < 1) {
            errors.add("checkpointInterval 必须 >= 1");
        }
        if (config.getRetainedCheckpoints() < 1) {
            errors.add("retainedCheckpoints 必须 >= 1");
        }
        if (config.getRdfBins() < 10) {
            errors.add("rdfBins 必须 >= 10");
        }
    }
}
