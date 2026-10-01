package com.github.guoyyit.moleculardynamicsjava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.moleculardynamicsjava.config.ConfigValidator;
import com.github.guoyyit.moleculardynamicsjava.engine.SimulationEngine;
import com.github.guoyyit.moleculardynamicsjava.model.JobStatus;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationManager {
    private final Path workRoot;
    private final Map<String, JobHandle> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final AtomicLong sequence = new AtomicLong();
    private final ObjectMapper mapper = new ObjectMapper();

    public SimulationManager(@Value("${md.work-dir:md-work}") String workDir,
                             @Value("${md.max-concurrent:4}") int maxConcurrent) {
        this.workRoot = Path.of(workDir).toAbsolutePath();
        this.executor = Executors.newFixedThreadPool(Math.max(1, maxConcurrent), runnable -> {
            Thread thread = new Thread(runnable, "md-worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public JobHandle submit(SimulationConfig config) {
        List<String> errors = new ConfigValidator().validate(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("配置校验失败:\n - " + String.join("\n - ", errors));
        }
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path jobDirectory = workRoot.resolve("jobs").resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
            persistConfig(jobDirectory, config);
        } catch (IOException error) {
            throw new IllegalStateException("无法创建任务目录: " + error.getMessage(), error);
        }
        JobHandle handle = new JobHandle(jobId, jobDirectory, config);
        jobs.put(jobId, handle);
        launch(handle, false, 0L);
        return handle;
    }

    public JobHandle get(String jobId) {
        JobHandle handle = jobs.get(jobId);
        if (handle == null) {
            throw new JobNotFoundException(jobId);
        }
        return handle;
    }

    public List<JobHandle> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(handle -> handle.id))
                .toList();
    }

    public boolean cancel(String jobId) {
        JobHandle handle = get(jobId);
        if (handle.status == JobStatus.PENDING || handle.status == JobStatus.RUNNING) {
            handle.cancelled.set(true);
            return true;
        }
        return false;
    }

    public JobHandle resume(String jobId, long additionalSteps) {
        JobHandle handle = get(jobId);
        if (handle.status == JobStatus.PENDING || handle.status == JobStatus.RUNNING) {
            throw new IllegalStateException("任务仍在运行中, 无法恢复: " + jobId);
        }
        launch(handle, true, additionalSteps);
        return handle;
    }

    private void launch(JobHandle handle, boolean resume, long additionalSteps) {
        handle.status = JobStatus.PENDING;
        handle.terminationReason = TerminationReason.NONE;
        handle.message = "";
        handle.cancelled.set(false);
        executor.submit(() -> {
            handle.status = JobStatus.RUNNING;
            try {
                SimulationEngine engine = new SimulationEngine();
                SimulationResult result = resume
                        ? engine.resume(handle.directory, additionalSteps, handle.cancelled)
                        : engine.run(handle.config, handle.directory, handle.cancelled);
                handle.result = result;
                handle.terminationReason = result.getTerminationReason();
                handle.message = result.getMessage();
                handle.status = mapStatus(result.getTerminationReason());
            } catch (Throwable error) {
                handle.failure = error;
                handle.terminationReason = TerminationReason.FAILED;
                handle.message = error.getClass().getSimpleName() + ": " + error.getMessage();
                handle.status = JobStatus.FAILED;
            }
        });
    }

    private JobStatus mapStatus(TerminationReason reason) {
        return switch (reason) {
            case COMPLETED -> JobStatus.COMPLETED;
            case CANCELLED -> JobStatus.CANCELLED;
            case TIMEOUT, DIVERGED -> JobStatus.TERMINATED;
            default -> JobStatus.FAILED;
        };
    }

    private void persistConfig(Path jobDirectory, SimulationConfig config) throws IOException {
        Path target = jobDirectory.resolve("input-config.json");
        Path temporary = jobDirectory.resolve(".input-config." + System.nanoTime() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), config);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
