package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.BuiltInExamples;
import com.github.guoyyit.moleculardynamicsjava.model.JobStatus;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.TerminationReason;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationJob;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationManager;
import com.github.guoyyit.moleculardynamicsjava.checkpoint.CheckpointManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {
    @Test
    void concurrentJobsRunIndependently(@TempDir Path tempDir) throws Exception {
        SimulationManager manager = new SimulationManager(tempDir.resolve("work").toString());
        int jobCount = 4;
        SimulationJob[] jobs = new SimulationJob[jobCount];
        for (int i = 0; i < jobCount; i++) {
            SimulationConfig config = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
            config.jobId = "concurrent-" + i;
            jobs[i] = manager.submit(config);
        }
        for (SimulationJob job : jobs) {
            assertTrue(job.awaitCompletion(60_000), "job " + job.getJobId() + " did not finish");
            assertEquals(JobStatus.FINISHED, job.getStatus());
            assertEquals(TerminationReason.COMPLETED, job.getTerminationReason());
            assertNotNull(job.getResult());
        }
        assertEquals(jobCount, manager.list().size());
        for (int i = 1; i < jobCount; i++) {
            DeterminismTest.assertIdentical(jobs[0].getResult(), jobs[i].getResult());
        }
    }

    @Test
    void cancellingOneJobDoesNotAffectOthers(@TempDir Path tempDir) throws Exception {
        SimulationManager manager = new SimulationManager(tempDir.resolve("work").toString());

        SimulationConfig cancelledConfig = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);
        cancelledConfig.jobId = "cancelled";
        cancelledConfig.duration = 100.0;
        SimulationJob cancelled = manager.submit(cancelledConfig);

        SimulationConfig normalConfig = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
        normalConfig.jobId = "normal";
        SimulationJob normal = manager.submit(normalConfig);

        Thread.sleep(50);
        cancelled.cancel();

        assertTrue(cancelled.awaitCompletion(60_000));
        assertTrue(normal.awaitCompletion(60_000));

        assertEquals(JobStatus.TERMINATED, cancelled.getStatus());
        assertEquals(TerminationReason.CANCELLED, cancelled.getTerminationReason());
        assertNotNull(cancelled.getResult());

        assertEquals(JobStatus.FINISHED, normal.getStatus());
        assertEquals(TerminationReason.COMPLETED, normal.getTerminationReason());
    }

    @Test
    void invalidSubmissionDoesNotAffectExistingJobs(@TempDir Path tempDir) throws Exception {
        SimulationManager manager = new SimulationManager(tempDir.resolve("work").toString());
        SimulationConfig good = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
        good.jobId = "good";
        SimulationJob goodJob = manager.submit(good);

        SimulationConfig bad = new BuiltInExamples().get(BuiltInExamples.FCC_32_NVE);
        bad.jobId = "bad";
        bad.species.get(0).mass = -3.0;
        try {
            manager.submit(bad);
            throw new AssertionError("expected invalid config to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("mass"));
        }

        assertTrue(goodJob.awaitCompletion(60_000));
        assertEquals(JobStatus.FINISHED, goodJob.getStatus());
        assertNotNull(manager.get("good"));
        assertNotEquals("good", "bad");
    }

    @Test
    void timeoutTerminatesJobAndLeavesRecoverableCheckpoint(@TempDir Path tempDir) throws Exception {
        Path workDir = tempDir.resolve("work-timeout");
        SimulationManager manager = new SimulationManager(workDir.toString());
        SimulationConfig config = new BuiltInExamples().get(BuiltInExamples.FCC_108_NVT);
        config.jobId = "timed-out";
        config.duration = 1000.0;
        config.timeoutMillis = 30.0;

        SimulationJob job = manager.submit(config);
        assertTrue(job.awaitCompletion(60_000));
        assertEquals(JobStatus.TERMINATED, job.getStatus());
        assertEquals(TerminationReason.TIMEOUT, job.getTerminationReason());

        CheckpointManager checkpoints =
                new CheckpointManager(workDir.resolve("timed-out").resolve("checkpoints"));
        assertNotNull(checkpoints.readLatest());
        assertTrue(checkpoints.readLatest().step > 0);
    }
}
