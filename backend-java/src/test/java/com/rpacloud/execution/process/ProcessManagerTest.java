package com.rpacloud.execution.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpacloud.execution.worker.WorkerHandle;
import com.rpacloud.execution.worker.WorkerKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessManagerTest {

    private ProcessManager pm;

    @BeforeEach
    void setUp() {
        pm = new ProcessManager();
    }

    @Test
    void isRunningReturnsFalseForUnknownFlow() {
        assertThat(pm.isRunning(999L)).isFalse();
    }

    @Test
    void registerAndIsRunning() throws Exception {
        // Start a long-running process
        Process process = new ProcessBuilder("java", "-version").start();
        process.waitFor(); // let it finish
        pm.register(1L, process);
        // Process already finished, isRunning should return false and clean up
        assertThat(pm.isRunning(1L)).isFalse();
    }

    @Test
    void doubleRegisterThrows() throws Exception {
        // Use a process that runs for a while
        ProcessBuilder pb = new ProcessBuilder("ping", "-n", "10", "127.0.0.1");
        Process process = pb.start();
        try {
            pm.register(1L, process);
            assertThat(pm.isRunning(1L)).isTrue();
            // Second register should throw
            assertThatThrownBy(() -> pm.register(1L, process))
                    .isInstanceOf(FlowAlreadyRunningException.class);
        } finally {
            process.destroyForcibly();
            pm.unregister(1L);
        }
    }

    @Test
    void forceStopKillsProcess() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ping", "-n", "10", "127.0.0.1");
        Process process = pb.start();
        pm.register(1L, process);
        assertThat(pm.isRunning(1L)).isTrue();
        boolean stopped = pm.forceStop(1L);
        assertThat(stopped).isTrue();
        assertThat(pm.isRunning(1L)).isFalse();
    }

    @Test
    void stopRequestTracking() {
        assertThat(pm.wasStopRequested(1L)).isFalse();
        pm.clearStopRequest(1L); // no-op on missing
        assertThat(pm.wasStopRequested(1L)).isFalse();
    }

    @Test
    void getRunningFlowIds() throws Exception {
        assertThat(pm.getRunningFlowIds()).isEmpty();
        ProcessBuilder pb = new ProcessBuilder("ping", "-n", "10", "127.0.0.1");
        Process p1 = pb.start();
        Process p2 = pb.start();
        try {
            pm.register(1L, p1);
            pm.register(2L, p2);
            assertThat(pm.getRunningFlowIds()).containsExactlyInAnyOrder(1L, 2L);
        } finally {
            p1.destroyForcibly();
            p2.destroyForcibly();
            pm.unregister(1L);
            pm.unregister(2L);
        }
    }

    // ---- Worker handle tracking (pooled path) ----

    @Test
    void registerWorker_andIsRunning() {
        var worker = new WorkerHandle(new WorkerKey("chromium", true, null));
        pm.registerWorker(10L, worker);
        assertThat(pm.isRunning(10L)).isTrue();
        assertThat(pm.getRunningFlowIds()).contains(10L);
    }

    @Test
    void forceStopWorker_destroysHandle() {
        var worker = new WorkerHandle(new WorkerKey("chromium", true, null));
        pm.registerWorker(10L, worker);
        assertThat(pm.isRunning(10L)).isTrue();
        boolean stopped = pm.forceStop(10L);
        assertThat(stopped).isTrue();
        assertThat(worker.isDestroyed()).isTrue();
        assertThat(pm.isRunning(10L)).isFalse();
    }

    @Test
    void unregisterWorker() {
        var worker = new WorkerHandle(new WorkerKey("chromium", true, null));
        pm.registerWorker(10L, worker);
        pm.unregisterWorker(10L);
        assertThat(pm.isRunning(10L)).isFalse();
    }
}
