package com.github.guoyyit.moleculardynamicsjava.tests;

import com.github.guoyyit.moleculardynamicsjava.examples.ExampleSystems;
import com.github.guoyyit.moleculardynamicsjava.model.JobStatus;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.service.JobHandle;
import com.github.guoyyit.moleculardynamicsjava.service.SimulationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {
    @Test
    void multipleJobsRunConcurrentlyAndStayIsolated(@TempDir Path tempDir) throws Exception {
        SimulationManager manager = new SimulationManager(tempDir.resolve("work").toString(), 4);
        List<JobHandle> handles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            SimulationConfig config = ExampleSystems.all().get("argon-fcc-nve");
            config.setName("concurrent-" + i);
            handles.add(manager.submit(config));
        }

        waitFor(handles, 30_000);
        for (JobHandle handle : handles) {
            assertEquals(JobStatus.COMPLETED, handle.status,
                    "任务 " + handle.id + " 应完成, 实际: " + handle.status
                            + " " + handle.message);
            assertEquals(2000L, handle.result.getSteps());
        }

        double firstEnergy = handles.get(0).result.getMeanTotalEnergy();
        double lastEnergy = handles.get(3).result.getMeanTotalEnergy();
        assertEquals(Double.doubleToLongBits(firstEnergy),
                Double.doubleToLongBits(lastEnergy),
                "相同配置的并发任务必须得到完全一致的确定性结果");
        assertNotEquals(handles.get(0).directory, handles.get(1).directory);
    }

    @Test
    void invalidJobIsRejectedAndDoesNotAffectOthers(@TempDir Path tempDir) throws Exception {
        SimulationManager manager = new SimulationManager(tempDir.resolve("work").toString(), 2);
        assertThrows(IllegalArgumentException.class, () -> manager.submit(null));

        SimulationConfig badConfig = ExampleSystems.all().get("argon-fcc-nve");
        badConfig.setTimeStep(-1.0);
        assertThrows(IllegalArgumentException.class, () -> manager.submit(badConfig));

        SimulationConfig goodConfig = ExampleSystems.all().get("argon-fcc-nve");
        JobHandle goodHandle = manager.submit(goodConfig);
        waitFor(List.of(goodHandle), 30_000);
        assertEquals(JobStatus.COMPLETED, goodHandle.status);
    }

    @Test
    void cancelledJobKeepsValidCheckpointAndOthersFinish(@TempDir Path tempDir) throws Exception {
        SimulationManager manager = new SimulationManager(tempDir.resolve("work").toString(), 4);
        SimulationConfig longConfig = ExampleSystems.all().get("argon-fcc-nve");
        longConfig.setSteps(20_000);
        longConfig.setCheckpointInterval(200);
        JobHandle longJob = manager.submit(longConfig);

        SimulationConfig shortConfig = ExampleSystems.all().get("argon-fcc-nve");
        JobHandle shortJob = manager.submit(shortConfig);

        Thread.sleep(80);
        assertTrue(manager.cancel(longJob.id));
        waitFor(List.of(longJob, shortJob), 30_000);

        assertEquals(JobStatus.CANCELLED, longJob.status);
        assertEquals(JobStatus.COMPLETED, shortJob.status);

        JobHandle resumed = manager.resume(longJob.id, 400);
        waitFor(List.of(resumed), 30_000);
        assertTrue(resumed.status == JobStatus.COMPLETED
                        || resumed.status == JobStatus.TERMINATED,
                "取消后最新检查点必须可恢复, 实际状态: " + resumed.status);
    }

    private void waitFor(List<JobHandle> handles, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean allDone = handles.stream().allMatch(handle ->
                    handle.status == JobStatus.COMPLETED
                            || handle.status == JobStatus.TERMINATED
                            || handle.status == JobStatus.CANCELLED
                            || handle.status == JobStatus.FAILED);
            if (allDone) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("等待任务结束超时");
    }
}
