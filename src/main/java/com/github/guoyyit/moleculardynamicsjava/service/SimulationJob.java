package com.github.guoyyit.moleculardynamicsjava.service;

import com.github.guoyyit.moleculardynamicsjava.model.JobStatus;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single simulation running on its own daemon thread.
 *
 * <p>All mutable state is confined to the worker; readers only observe
 * references published safely through synchronization. A failure is caught
 * here and recorded, never propagated to sibling jobs.
 */
public class SimulationJob implements SimulationEngine.RunControl {
    private final String jobId;
    private final SimulationConfig config;
    private final SimulationEngine engine;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final long startNanos;
    private final long timeoutNanos;

    private volatile JobStatus status = JobStatus.QUEUED;
    private volatile TerminationReason terminationReason;
    private volatile SimulationResult result;
    private volatile String errorMessage;
    private Thread worker;

    public SimulationJob(String jobId, SimulationConfig config, Path workDirectory) {
        this.jobId = jobId;
        this.config = config;
        this.engine = new SimulationEngine(config, workDirectory.resolve("checkpoints"));
        this.startNanos = System.nanoTime();
        this.timeoutNanos = config.timeoutMillis > 0.0
                ? (long) (config.timeoutMillis * 1_000_000L) : Long.MAX_VALUE;
    }

    void start() {
        status = JobStatus.RUNNING;
        worker = new Thread(this::execute, "md-job-" + jobId);
        worker.setDaemon(true);
        worker.start();
    }

    private void execute() {
        try {
            SimulationResult produced = engine.run(this);
            result = produced;
            terminationReason = engine.getTerminationReason();
            if (terminationReason == TerminationReason.COMPLETED) {
                status = JobStatus.FINISHED;
            } else {
                status = JobStatus.TERMINATED;
            }
        } catch (RuntimeException e) {
            errorMessage = rootMessage(e);
            terminationReason = TerminationReason.FAILED;
            status = JobStatus.FAILED;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null ? message : current.getClass().getSimpleName();
    }

    public String getJobId() {
        return jobId;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public JobStatus getStatus() {
        return status;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public SimulationResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean awaitCompletion(long timeoutMillis) throws InterruptedException {
        Thread thread = worker;
        if (thread == null) {
            return false;
        }
        thread.join(timeoutMillis);
        return status != JobStatus.RUNNING && status != JobStatus.QUEUED;
    }

    @Override
    public boolean isCancelled(long completedSteps) {
        return cancelled.get();
    }

    @Override
    public boolean isTimedOut(long completedSteps) {
        return System.nanoTime() - startNanos > timeoutNanos;
    }
}
