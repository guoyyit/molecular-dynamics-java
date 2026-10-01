package com.github.guoyyit.moleculardynamicsjava.engine;

import com.github.guoyyit.moleculardynamicsjava.analysis.MeanSquareDisplacement;
import com.github.guoyyit.moleculardynamicsjava.analysis.RadialDistributionFunction;
import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointStore;
import com.github.guoyyit.moleculardynamicsjava.config.ConfigValidator;
import com.github.guoyyit.moleculardynamicsjava.integrator.Integrator;
import com.github.guoyyit.moleculardynamicsjava.integrator.LangevinIntegrator;
import com.github.guoyyit.moleculardynamicsjava.integrator.VelocityVerletIntegrator;
import com.github.guoyyit.moleculardynamicsjava.model.Ensemble;
import com.github.guoyyit.moleculardynamicsjava.model.ParticleSpec;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationState;
import com.github.guoyyit.moleculardynamicsjava.model.Species;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.output.ResultWriter;
import com.github.guoyyit.moleculardynamicsjava.physics.LennardJonesForce;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRng;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimulationEngine {
    private static final int MSD_MAX_LAGS = 128;

    private final LennardJonesForce forceField = new LennardJonesForce();
    private final VelocityVerletIntegrator verlet = new VelocityVerletIntegrator();
    private final LangevinIntegrator langevin = new LangevinIntegrator();

    public SimulationResult run(SimulationConfig config, Path jobDirectory,
                                AtomicBoolean cancelled) {
        List<String> errors = new ConfigValidator().validate(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "配置校验失败:\n - " + String.join("\n - ", errors));
        }
        SimulationState state = initialize(config);
        return execute(config, state, jobDirectory, cancelled, config.getSteps(), true);
    }

    public SimulationResult resume(Path jobDirectory, long additionalSteps,
                                  AtomicBoolean cancelled) {
        CheckpointStore store = new CheckpointStore(jobDirectory.resolve("checkpoints"), 1);
        CheckpointStore.Checkpoint checkpoint;
        try {
            checkpoint = store.latest();
        } catch (IOException error) {
            throw new IllegalStateException("读取检查点失败: " + error.getMessage(), error);
        }
        if (checkpoint == null) {
            throw new IllegalStateException("目录中没有可用于恢复的有效检查点: " + jobDirectory);
        }
        SimulationConfig config = checkpoint.config;
        SimulationState state = checkpoint.state;
        long steps = additionalSteps > 0 ? additionalSteps : config.getSteps();
        return execute(config, state, jobDirectory, cancelled, steps, false);
    }

    private SimulationState initialize(SimulationConfig config) {
        SimulationState state = new SimulationState();
        int n = config.getParticles().size();
        state.n = n;
        state.masses = new double[n];
        state.pos = new double[3 * n];
        state.vel = new double[3 * n];
        state.setForce(new double[3 * n]);
        state.unwrapped = new double[3 * n];
        state.msdReference = new double[3 * n];
        Map<String, Double> massByName = new HashMap<>();
        for (Species species : config.getSpecies()) {
            massByName.put(species.name(), species.mass());
        }
        double[] totalMomentum = new double[3];
        double totalMass = 0.0;
        for (int i = 0; i < n; i++) {
            ParticleSpec particle = config.getParticles().get(i);
            state.masses[i] = massByName.get(particle.species());
            totalMass += state.masses[i];
            double[] position = particle.position();
            System.arraycopy(position, 0, state.pos, 3 * i, 3);
            System.arraycopy(position, 0, state.unwrapped, 3 * i, 3);
            double[] velocity = particle.velocity();
            if (velocity != null) {
                System.arraycopy(velocity, 0, state.vel, 3 * i, 3);
            }
            for (int axis = 0; axis < 3; axis++) {
                totalMomentum[axis] += state.masses[i] * state.vel[3 * i + axis];
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            totalMomentum[axis] /= totalMass;
        }
        for (int i = 0; i < n; i++) {
            for (int axis = 0; axis < 3; axis++) {
                state.vel[3 * i + axis] -= totalMomentum[axis];
            }
        }
        double currentKinetic = kineticEnergy(state);
        if (config.getEnsemble() == Ensemble.NVT
                && config.getTargetTemperature() != null
                && currentKinetic > 0.0) {
            double currentTemperature = 2.0 * currentKinetic / degreesOfFreedom(n);
            double scale = Math.sqrt(config.getTargetTemperature() / currentTemperature);
            for (int i = 0; i < state.vel.length; i++) {
                state.vel[i] *= scale;
            }
        }
        System.arraycopy(state.unwrapped, 0, state.msdReference, 0, 3 * n);
        forceField.computeForcesAndEnergy(state, config);
        return state;
    }

    private SimulationResult execute(SimulationConfig config, SimulationState state, Path jobDirectory,
                                    AtomicBoolean cancelled, long steps, boolean freshRun) {
        long startStep = state.step;
        long targetStep = startStep + steps;
        DeterministicRng rng = new DeterministicRng(config.getSeed());
        if (state.step > 0) {
            rng.setState(state.rngState);
        }
        Integrator integrator = config.getEnsemble() == Ensemble.NVT ? langevin : verlet;

        double halfMinEdge = Math.min(config.getBox()[0],
                Math.min(config.getBox()[1], config.getBox()[2])) / 2.0;
        double rdfMaxDistance = Math.min(halfMinEdge, 1.8 * config.getSigma());
        RadialDistributionFunction rdf;
        MeanSquareDisplacement msd = new MeanSquareDisplacement(MSD_MAX_LAGS);
        if (freshRun) {
            rdf = new RadialDistributionFunction(config.getRdfBins(), rdfMaxDistance);
            msd.setReference(state.msdReference, state.n);
        } else {
            rdf = new RadialDistributionFunction(config.getRdfBins(), rdfMaxDistance,
                    state.rdfHistogram, state.rdfFrames);
            msd.restore(state.msdReference, state.n, state.msdSteps, state.msdTimes,
                    state.msdValues);
        }

        CheckpointStore store = new CheckpointStore(jobDirectory.resolve("checkpoints"),
                config.getRetainedCheckpoints());
        double potentialEnergy = forceField.computeForcesAndEnergy(state, config);
        if (freshRun) {
            recordSample(config, state, potentialEnergy, rdf, msd, startStep);
            snapshotAnalysis(state, rdf, msd);
        }

        TerminationReason reason = TerminationReason.COMPLETED;
        String terminationMessage = "";
        long deadline = config.getTimeoutMillis() > 0
                ? System.nanoTime() + config.getTimeoutMillis() * 1_000_000L : Long.MAX_VALUE;
        try {
            while (state.step < targetStep) {
                potentialEnergy = integrator.step(state, config, rng, forceField);
                state.rngState = rng.getState();
                if (state.step % config.getSampleInterval() == 0) {
                    recordSample(config, state, potentialEnergy, rdf, msd, startStep);
                }
                String divergence = detectDivergence(config, state, potentialEnergy);
                if (divergence != null) {
                    reason = TerminationReason.DIVERGED;
                    terminationMessage = divergence;
                    break;
                }
                if (cancelled.get()) {
                    reason = TerminationReason.CANCELLED;
                    terminationMessage = "任务被用户取消";
                    break;
                }
                if (System.nanoTime() >= deadline) {
                    reason = TerminationReason.TIMEOUT;
                    terminationMessage = "运行超时 (" + config.getTimeoutMillis() + " ms)";
                    break;
                }
                if (state.step % config.getCheckpointInterval() == 0) {
                    snapshotAnalysis(state, rdf, msd);
                    try {
                        store.commit(state, config);
                    } catch (IOException error) {
                        reason = TerminationReason.FAILED;
                        terminationMessage = "写入检查点失败: " + error.getMessage();
                        break;
                    }
                }
            }
        } catch (RuntimeException error) {
            reason = TerminationReason.FAILED;
            terminationMessage = error.getClass().getSimpleName() + ": " + error.getMessage();
        }

        try {
            snapshotAnalysis(state, rdf, msd);
            store.commit(state, config);
        } catch (IOException error) {
            if (reason == TerminationReason.COMPLETED) {
                reason = TerminationReason.FAILED;
                terminationMessage = "写入最终检查点失败: " + error.getMessage();
            }
        }

        SimulationResult result = buildResult(config, state, rdf, msd, reason,
                terminationMessage, jobDirectory);
        try {
            new ResultWriter().write(jobDirectory.resolve("output"), result);
        } catch (IOException error) {
            if (reason == TerminationReason.COMPLETED) {
                result.setTerminationReason(TerminationReason.FAILED);
                result.setMessage("写出结果失败: " + error.getMessage());
            }
        }
        return result;
    }

    private void recordSample(SimulationConfig config, SimulationState state,
                              double potentialEnergy, RadialDistributionFunction rdf,
                              MeanSquareDisplacement msd, long startStep) {
        double kineticEnergy = kineticEnergy(state);
        double temperature = 2.0 * kineticEnergy / degreesOfFreedom(state.n);
        state.samples.add(new com.github.guoyyit.moleculardynamicsjava.model.Sample(
                state.step, state.time, temperature,
                kineticEnergy, potentialEnergy, kineticEnergy + potentialEnergy));
        rdf.addFrame(state.pos, config.getBox(), state.n);
        long lag = state.step - startStep;
        msd.addFrame(lag, lag * config.getTimeStep(), state.unwrapped, state.n);
    }

    private void snapshotAnalysis(SimulationState state, RadialDistributionFunction rdf,
                                  MeanSquareDisplacement msd) {
        state.rdfHistogram = rdf.histogram();
        state.rdfFrames = rdf.frames();
        state.msdReference = msd.reference();
        state.msdSteps = msd.steps();
        state.msdTimes = msd.times();
        state.msdValues = msd.values();
    }

    private String detectDivergence(SimulationConfig config, SimulationState state,
                                    double potentialEnergy) {
        for (int i = 0; i < state.vel.length; i++) {
            if (!Double.isFinite(state.vel[i]) || !Double.isFinite(state.pos[i])
                    || !Double.isFinite(state.getForce()[i])
                    || !Double.isFinite(potentialEnergy)) {
                return "数值发散: 在第 " + state.step + " 步检测到 NaN/Infinity";
            }
        }
        if (config.getMaxTemperature() != null) {
            double temperature = 2.0 * kineticEnergy(state) / degreesOfFreedom(state.n);
            if (temperature > config.getMaxTemperature()) {
                return "数值发散: 温度 " + temperature + " 超过上限 " + config.getMaxTemperature();
            }
        }
        double maxPosition = 0.0;
        for (double value : state.unwrapped) {
            maxPosition = Math.max(maxPosition, Math.abs(value));
        }
        if (maxPosition > config.getMaxUnwrappedDistance()) {
            return "数值发散: 未折叠坐标超过上限 " + config.getMaxUnwrappedDistance();
        }
        return null;
    }

    private SimulationResult buildResult(SimulationConfig config, SimulationState state,
                                         RadialDistributionFunction rdf,
                                         MeanSquareDisplacement msd,
                                         TerminationReason reason, String message,
                                         Path jobDirectory) {
        SimulationResult result = new SimulationResult();
        result.setJobId(jobDirectory.getFileName().toString());
        result.setName(config.getName());
        result.setEnsemble(config.getEnsemble());
        result.setParticles(state.n);
        result.setSteps(state.step);
        result.setFinalTime(state.time);
        result.setTerminationReason(reason);
        result.setMessage(message);
        result.setSamples(List.copyOf(state.samples));

        double volume = config.getBox()[0] * config.getBox()[1] * config.getBox()[2];
        double[] g = rdf.finish(state.n, volume);
        double[] edges = new double[g.length];
        double binWidth = g.length == 0 ? 0.0 : rdf.maxDistance() / g.length;
        for (int i = 0; i < edges.length; i++) {
            edges[i] = (i + 0.5) * binWidth;
        }
        result.setRdfEdges(edges);
        result.setRdfValues(g);
        result.setMsdTimes(msd.times());
        result.setMsdValues(msd.values());

        double temperatureSum = 0.0;
        double energySum = 0.0;
        for (com.github.guoyyit.moleculardynamicsjava.model.Sample sample : state.samples) {
            temperatureSum += sample.temperature();
            energySum += sample.totalEnergy();
        }
        int count = Math.max(1, state.samples.size());
        result.setMeanTemperature(temperatureSum / count);
        result.setMeanTotalEnergy(energySum / count);
        if (state.samples.size() > 1) {
            double first = state.samples.get(0).totalEnergy();
            double last = state.samples.get(state.samples.size() - 1).totalEnergy();
            result.setEnergyDriftPerParticle(Math.abs(last - first) / state.n);
        }
        return result;
    }

    private double kineticEnergy(SimulationState state) {
        double kinetic = 0.0;
        for (int i = 0; i < state.n; i++) {
            kinetic += 0.5 * state.masses[i]
                    * (state.vel[3 * i] * state.vel[3 * i]
                    + state.vel[3 * i + 1] * state.vel[3 * i + 1]
                    + state.vel[3 * i + 2] * state.vel[3 * i + 2]);
        }
        return kinetic;
    }

    static double degreesOfFreedom(int n) {
        return Math.max(1.0, 3.0 * n - 3.0);
    }
}
