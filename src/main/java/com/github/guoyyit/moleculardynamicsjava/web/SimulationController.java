package com.github.guoyyit.moleculardynamicsjava.web;

import com.github.guoyyit.moleculardynamicsjava.service.SimulationJob;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationManager;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;
import com.github.guoyyit.moleculardynamicsjava.web.dto.JobRequest;
import com.github.guoyyit.moleculardynamicsjava.web.dto.JobView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Local HTTP API for submitting and inspecting molecular dynamics jobs.
 * All computation stays in this process; the service never calls out.
 */
@RestController
@RequestMapping("/api/jobs")
public class SimulationController {
    private final SimulationManager manager;

    public SimulationController(SimulationManager manager) {
        this.manager = manager;
    }

    @GetMapping("/examples")
    public java.util.Map<String, Object> examples() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("examples", manager.examples().keySet());
        return body;
    }

    @PostMapping
    public JobView submit(@RequestBody JobRequest request) {
        SimulationJob job = manager.submit(request.config, request.exampleName, request.jobId,
                request.seed, request.duration, request.timeoutMillis);
        return JobView.of(job, false);
    }

    @GetMapping
    public List<JobView> list() {
        return manager.list().stream().map(job -> JobView.of(job, false)).toList();
    }

    @GetMapping("/{jobId}")
    public JobView status(@PathVariable String jobId) {
        SimulationJob job = requireJob(jobId);
        return JobView.of(job, true);
    }

    @PostMapping("/{jobId}/cancel")
    public JobView cancel(@PathVariable String jobId) {
        SimulationJob job = requireJob(jobId);
        job.cancel();
        return JobView.of(job, false);
    }

    @GetMapping(value = "/{jobId}/timeseries", produces = "text/csv")
    public ResponseEntity<String> timeSeries(@PathVariable String jobId) {
        SimulationResult result = requireResult(jobId);
        return csv(result.toTimeSeriesCsv(), jobId + "-timeseries.csv");
    }

    @GetMapping(value = "/{jobId}/rdf", produces = "text/csv")
    public ResponseEntity<String> rdf(@PathVariable String jobId) {
        SimulationResult result = requireResult(jobId);
        return csv(result.toRdfCsv(), jobId + "-rdf.csv");
    }

    @GetMapping(value = "/{jobId}/msd", produces = "text/csv")
    public ResponseEntity<String> msd(@PathVariable String jobId) {
        SimulationResult result = requireResult(jobId);
        return csv(result.toMsdCsv(), jobId + "-msd.csv");
    }

    private SimulationJob requireJob(String jobId) {
        SimulationJob job = manager.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("no such job: " + jobId);
        }
        return job;
    }

    private SimulationResult requireResult(String jobId) {
        SimulationJob job = requireJob(jobId);
        SimulationResult result = job.getResult();
        if (result == null) {
            throw new IllegalStateException("job " + jobId + " has not produced a result yet (status "
                    + job.getStatus() + ")");
        }
        return result;
    }

    private static ResponseEntity<String> csv(String content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
    }
}
