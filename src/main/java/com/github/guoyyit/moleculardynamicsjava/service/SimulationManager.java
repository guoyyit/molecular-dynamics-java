package com.github.guoyyit.moleculardynamicsjava.service;

import com.github.guoyyit.moleculardynamicsjava.config.ConfigValidator;
import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of all simulation jobs. Each job gets a dedicated work
 * directory and worker thread, so jobs cannot share mutable state and a
 * failure or cancellation in one job never affects the others.
 */
@Service
public class SimulationManager {
    private final Map<String, SimulationJob> jobs = new ConcurrentHashMap<>();
    private final ConfigValidator validator = new ConfigValidator();
    private final BuiltInExamples examples = new BuiltInExamples();
    private final Path workRoot;

    public SimulationManager(@Value("${md.work-dir:target/md-work}") String workDir) {
        this.workRoot = Paths.get(workDir);
    }

    public SimulationJob submit(SimulationConfig config) {
        return submit(config, null, null, null, null, null);
    }

    /**
     * Submit either a full configuration ({@code config} non-null) or a named
     * built-in example, with optional scalar overrides.
     */
    public SimulationJob submit(SimulationConfig config, String exampleName, String jobId,
                                Long seed, Double duration, Double timeoutMillis) {
        SimulationConfig effective = config;
        if (exampleName != null && !exampleName.isBlank()) {
            SimulationConfig example = examples.get(exampleName);
            if (example == null) {
                throw new IllegalArgumentException("unknown example name '" + exampleName
                        + "', available: " + examples.all().keySet());
            }
            effective = example;
            if (seed != null) {
                effective.seed = seed;
            }
            if (duration != null) {
                effective.duration = duration;
            }
            if (timeoutMillis != null) {
                effective.timeoutMillis = timeoutMillis;
            }
        } else if (effective == null) {
            throw new IllegalArgumentException("request must contain either 'config' or 'exampleName'");
        } else {
            if (seed != null) {
                effective.seed = seed;
            }
            if (duration != null) {
                effective.duration = duration;
            }
            if (timeoutMillis != null) {
                effective.timeoutMillis = timeoutMillis;
            }
        }
        List<String> errors = validator.validate(effective);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("invalid simulation configuration:\n - "
                    + String.join("\n - ", errors));
        }
        String resolvedId = jobId != null && !jobId.isBlank()
                ? jobId
                : (effective.jobId != null && !effective.jobId.isBlank()
                        ? effective.jobId
                        : "job-" + Long.toHexString(System.nanoTime()));
        if (jobs.containsKey(resolvedId)) {
            throw new IllegalArgumentException("job id '" + resolvedId + "' is already in use");
        }
        effective.jobId = resolvedId;
        Path workDirectory = workRoot.resolve(sanitize(resolvedId));
        SimulationJob job = new SimulationJob(resolvedId, effective, workDirectory);
        jobs.put(resolvedId, job);
        job.start();
        return job;
    }

    public SimulationJob get(String jobId) {
        return jobs.get(jobId);
    }

    public Collection<SimulationJob> list() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public Map<String, SimulationConfig> examples() {
        return examples.all();
    }

    private static String sanitize(String jobId) {
        return jobId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }
}
