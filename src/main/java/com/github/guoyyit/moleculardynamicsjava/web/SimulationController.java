package com.github.guoyyit.moleculardynamicsjava.web;

import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.JobStatus;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.service.JobHandle;
import com.github.guoyyit.moleculardynamicsjava.service.JobNotFoundException;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulationController {
    private final SimulationManager manager;

    public SimulationController(SimulationManager manager) {
        this.manager = manager;
    }

    @PostMapping("/simulations")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SimulationConfig config) {
        JobHandle handle = manager.submit(config);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(describe(handle, false));
    }

    @GetMapping("/simulations")
    public Map<String, Object> list() {
        List<Map<String, Object>> jobs = manager.list().stream()
                .map(handle -> describe(handle, false))
                .toList();
        return Map.of("jobs", jobs);
    }

    @GetMapping("/simulations/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return describe(manager.get(id), true);
    }

    @PostMapping("/simulations/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        boolean requested = manager.cancel(id);
        return Map.of("cancelRequested", requested, "job", describe(manager.get(id), false));
    }

    @PostMapping("/simulations/{id}/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable String id,
                                                      @RequestParam(defaultValue = "0") long steps) {
        JobHandle handle = manager.resume(id, steps);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(describe(handle, false));
    }

    @GetMapping("/examples")
    public Map<String, Object> examples() {
        Map<String, Object> body = new LinkedHashMap<>();
        ExampleSystems.all().forEach((name, config) -> body.put(name,
                Map.of("name", config.getName(),
                        "particles", config.getParticles().size(),
                        "ensemble", config.getEnsemble().name(),
                        "steps", config.getSteps(),
                        "timeStep", config.getTimeStep(),
                        "seed", config.getSeed())));
        return Map.of("examples", body);
    }

    @GetMapping("/examples/{name}")
    public SimulationConfig example(@PathVariable String name) {
        SimulationConfig config = ExampleSystems.all().get(name);
        if (config == null) {
            throw new JobNotFoundException(name);
        }
        return config;
    }

    private Map<String, Object> describe(JobHandle handle, boolean includeResult) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", handle.id);
        body.put("name", handle.config.getName());
        body.put("status", handle.status.name());
        body.put("terminationReason", handle.terminationReason.name());
        body.put("message", handle.message);
        body.put("particles", handle.config.getParticles().size());
        body.put("ensemble", handle.config.getEnsemble().name());
        body.put("workDirectory", handle.directory.toString());
        if (includeResult && handle.result != null) {
            body.put("result", summarizeResult(handle.result));
        }
        if (handle.failure != null) {
            body.put("failure", handle.failure.getClass().getSimpleName()
                    + ": " + handle.failure.getMessage());
        }
        return body;
    }

    private Map<String, Object> summarizeResult(SimulationResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("steps", result.getSteps());
        summary.put("finalTime", result.getFinalTime());
        summary.put("meanTemperature", result.getMeanTemperature());
        summary.put("meanTotalEnergy", result.getMeanTotalEnergy());
        summary.put("energyDriftPerParticle", result.getEnergyDriftPerParticle());
        summary.put("terminationReason", result.getTerminationReason().name());
        summary.put("message", result.getMessage());
        return summary;
    }

    @RestControllerAdvice
    static class ApiExceptionHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(errorBody(error));
        }

        @ExceptionHandler(JobNotFoundException.class)
        public ResponseEntity<Map<String, Object>> notFound(JobNotFoundException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(error));
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<Map<String, Object>> conflict(IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(error));
        }

        private Map<String, Object> errorBody(Exception error) {
            return Map.of("error", error.getMessage() == null ? "" : error.getMessage());
        }
    }
}
