package com.github.guoyyit.moleculardynamicsjava.web.dto;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;

/** Request body for POST /api/jobs: either a full config or an example name. */
public class JobRequest {
    public SimulationConfig config;
    public String exampleName;
    public String jobId;
    public Long seed;
    public Double duration;
    public Double timeoutMillis;

    public JobRequest() {
    }
}
