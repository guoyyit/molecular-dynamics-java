package com.github.guoyyit.moleculardynamicsjava.service;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("任务不存在: " + jobId);
    }
}
