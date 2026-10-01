package com.github.guoyyit.moleculardynamicsjava.web.dto;

import com.github.guoyyit.moleculardynamicsjava.service.SimulationJob;
import com.github.guoyyit.moleculardynamicsjava.stats.SimulationResult;

/**
 * JSON view of a job. The full {@link SimulationResult} is embedded once the
 * run reaches a terminal state, so clients can poll status cheaply.
 */
public class JobView {
    public String jobId;
    public String status;
    public String terminationReason;
    public String errorMessage;
    public SimulationResult result;

    public JobView() {
    }

    public static JobView of(SimulationJob job, boolean includeResult) {
        JobView view = new JobView();
        view.jobId = job.getJobId();
        view.status = job.getStatus().name();
        view.terminationReason = job.getTerminationReason() == null
                ? null : job.getTerminationReason().name();
        view.errorMessage = job.getErrorMessage();
        if (includeResult) {
            view.result = job.getResult();
        }
        return view;
    }
}
