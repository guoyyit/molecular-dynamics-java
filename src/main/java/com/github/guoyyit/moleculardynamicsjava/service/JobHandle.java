package com.github.guoyyit.moleculardynamicsjava.service;

import com.github.guoyyit.moleculardynamicsjava.model.JobStatus;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobHandle {
    public final String id;
    public final Path directory;
    public final SimulationConfig config;
    public final AtomicBoolean cancelled = new AtomicBoolean(false);
    public volatile JobStatus status = JobStatus.PENDING;
    public volatile TerminationReason terminationReason = TerminationReason.NONE;
    public volatile String message = "";
    public volatile SimulationResult result;
    public volatile Throwable failure;

    public JobHandle(String id, Path directory, SimulationConfig config) {
        this.id = id;
        this.directory = directory;
        this.config = config;
    }
}
