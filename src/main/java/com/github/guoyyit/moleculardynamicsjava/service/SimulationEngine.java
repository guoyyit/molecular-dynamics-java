package com.github.guoyyit.moleculardynamicsjava.service;

import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointData;
import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointManager;
import com.github.guoyyit.moleculardynamicsjava.config.VelocityInitializer;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.physics.Integrator;
import com.github.guoyyit.moleculardynamicsjava.physics.LennardJones;
import com.github.guoyyit.moleculardynamicsjava.physics.Thermostat;
import com.github.guoyyit.moleculardynamicsjava.rng.DeterministicRandom;
import com.github.guoyyit.moleculardynamicsjava.stats.MsdCalculator;
import com.github.guoyyit.moleculardynamicsjava.stats.RdfCalculator;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.stats.TrajectorySample;

import java.nio.file.Path;

/**
 * Runs one simulation to completion (or controlled termination) entirely
 * in-process. The same step sequence is produced whether or not the run is
 * interrupted and resumed, because every checkpoint carries the exact RNG
 * state and full dynamic state.
 */
public class SimulationEngine {
    private final SimulationConfig config;
    private final CheckpointManager checkpoints;

    private TerminationReason terminationReason = TerminationReason.COMPLETED;

    public SimulationEngine(SimulationConfig config, Path checkpointDirectory) {
        this.config = config;
        this.checkpoints = new CheckpointManager(checkpointDirectory);
    }

    public interface RunControl {
        boolean isCancelled(long completedSteps);

        boolean isTimedOut(long completedSteps);
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public SimulationResult run(RunControl control) {
        DeterministicRandom random = new DeterministicRandom(config.seed);
        CheckpointData checkpoint = checkpoints.readLatest();
        boolean resuming = checkpoint != null;
        if (!resuming) {
            prepareInitialState(random);
        }
        LennardJones potential = new LennardJones(config);
        Thermostat thermostat = new Thermostat(config, random);
        Integrator integrator = new Integrator(config, random, potential, thermostat);

        long step = 0;
        if (resuming) {
            integrator.restore(checkpoint.positions, checkpoint.unwrappedPositions,
                    checkpoint.velocities);
            random.setState(checkpoint.rngState);
            step = checkpoint.step;
        }

        long totalSteps = StrictMath.max(1, StrictMath.round(config.duration / config.timeStep));
        RdfCalculator rdf = new RdfCalculator(config);
        MsdCalculator msd = new MsdCalculator(config);
        SimulationResult result = new SimulationResult();
        result.jobId = config.jobId;
        result.ensemble = config.ensemble;
        result.targetTemperature = config.targetTemperature;
        result.resumedFromStep = step;

        if (step == 0) {
            recordSample(result, integrator, 0);
            rdf.accumulate(integrator.getPositions());
            msd.accumulate(0, integrator.getUnwrappedPositions());
            checkpoints.write(snapshot(0, random, integrator, result, rdf, msd));
        } else {
            result.samples.addAll(checkpoint.committedSamples);
            rdf.restore(extractHistogram(checkpoint, rdf), checkpoint.rdfFrames);
            msd.restore(checkpoint.msdLagSteps, checkpoint.msdValues, checkpoint.msdFilled);
            msd.restoreOrigin(checkpoint.msdOrigin);
        }

        while (step < totalSteps) {
            integrator.step();
            step++;

            if (isDivergent(integrator)) {
                terminationReason = TerminationReason.NUMERICAL_DIVERGENCE;
                break;
            }
            if (step % config.sampleInterval == 0 || step == totalSteps) {
                recordSample(result, integrator, step);
                rdf.accumulate(integrator.getPositions());
                msd.accumulate(step, integrator.getUnwrappedPositions());
            }
            if (step % config.checkpointInterval == 0 && step < totalSteps) {
                checkpoints.write(snapshot(step, random, integrator, result, rdf, msd));
            }
            if (step < totalSteps
                    && (control.isCancelled(step) || control.isTimedOut(step))) {
                checkpoints.write(snapshot(step, random, integrator, result, rdf, msd));
                terminationReason = control.isTimedOut(step)
                        ? TerminationReason.TIMEOUT : TerminationReason.CANCELLED;
                break;
            }
        }

        if (terminationReason == TerminationReason.COMPLETED) {
            checkpoints.write(snapshot(step, random, integrator, result, rdf, msd));
        }

        result.steps = step;
        result.duration = step * config.timeStep;
        result.terminationReason = terminationReason.name();
        result.rdfValues = rdf.finish();
        result.rdfBinWidth = rdf.getBinWidth();
        result.rdfBinEdges = new double[result.rdfValues.length];
        for (int i = 0; i < result.rdfBinEdges.length; i++) {
            result.rdfBinEdges[i] = (i + 0.5) * rdf.getBinWidth();
        }
        long[] lagSteps = msd.getLagSteps();
        result.msdValues = msd.finish();
        result.msdTimes = new double[lagSteps.length];
        for (int i = 0; i < lagSteps.length; i++) {
            result.msdTimes[i] = lagSteps[i] * config.timeStep;
        }
        return result;
    }

    private void prepareInitialState(DeterministicRandom random) {
        boolean velocitiesProvided = false;
        for (var particle : config.particles) {
            double speedSquared = particle.velocity[0] * particle.velocity[0]
                    + particle.velocity[1] * particle.velocity[1]
                    + particle.velocity[2] * particle.velocity[2];
            if (speedSquared > 0.0) {
                velocitiesProvided = true;
                break;
            }
        }
        if (!velocitiesProvided) {
            new VelocityInitializer().initializeVelocities(config, random);
        }
    }

    private CheckpointData snapshot(long step, DeterministicRandom random, Integrator integrator,
                                    SimulationResult result, RdfCalculator rdf, MsdCalculator msd) {
        CheckpointData data = new CheckpointData(step, random.getState(),
                copy(integrator.getPositions()),
                copy(integrator.getUnwrappedPositions()),
                copy(integrator.getVelocities()));
        data.committedSamples = new java.util.ArrayList<>(result.samples);
        data.rdfFrames = rdf.getFrames();
        data.rdfHistogram = rdf.getHistogram();
        data.msdLagSteps = msd.getLagSteps();
        data.msdValues = msd.getValues();
        data.msdFilled = msd.getFilled();
        data.msdOrigin = msd.getOrigin();
        return data;
    }

    private static long[] extractHistogram(CheckpointData checkpoint, RdfCalculator rdf) {
        if (checkpoint.rdfHistogram == null
                || checkpoint.rdfHistogram.length != rdf.getBinCount()) {
            throw new IllegalStateException("checkpoint RDF state is incompatible with configuration");
        }
        return checkpoint.rdfHistogram;
    }

    private static double[][] copy(double[][] source) {
        double[][] target = new double[source.length][3];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, target[i], 0, 3);
        }
        return target;
    }

    private void recordSample(SimulationResult result, Integrator integrator, long step) {
        double kinetic = 0.0;
        double[] masses = integrator.getMasses();
        double[][] velocities = integrator.getVelocities();
        for (int i = 0; i < velocities.length; i++) {
            kinetic += 0.5 * masses[i]
                    * (velocities[i][0] * velocities[i][0]
                    + velocities[i][1] * velocities[i][1]
                    + velocities[i][2] * velocities[i][2]);
        }
        double temperature = 2.0 * kinetic / (3.0 * velocities.length);
        double potential = integrator.getPotentialEnergy();
        result.samples.add(new TrajectorySample(step, step * config.timeStep,
                temperature, potential, kinetic));
    }

    private boolean isDivergent(Integrator integrator) {
        double[][] velocities = integrator.getVelocities();
        double kinetic = 0.0;
        double[] masses = integrator.getMasses();
        for (int i = 0; i < velocities.length; i++) {
            kinetic += 0.5 * masses[i]
                    * (velocities[i][0] * velocities[i][0]
                    + velocities[i][1] * velocities[i][1]
                    + velocities[i][2] * velocities[i][2]);
        }
        double potential = integrator.getPotentialEnergy();
        if (!Double.isFinite(potential) || !Double.isFinite(kinetic)) {
            return true;
        }
        double limit = config.maxEnergyPerParticle * velocities.length;
        if (StrictMath.abs(potential) > limit || kinetic > limit) {
            return true;
        }
        for (double[] velocity : velocities) {
            for (double component : velocity) {
                if (!Double.isFinite(component)) {
                    return true;
                }
            }
        }
        double[][] positions = integrator.getPositions();
        for (double[] position : positions) {
            for (double component : position) {
                if (!Double.isFinite(component)) {
                    return true;
                }
            }
        }
        return false;
    }
}
