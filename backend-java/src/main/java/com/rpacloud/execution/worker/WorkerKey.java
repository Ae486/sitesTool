package com.rpacloud.execution.worker;

/**
 * Classification key for heterogeneous Worker sub-pools.
 * Flows with the same WorkerKey can share the same Worker.
 *
 * <p>Proxy is NOT part of the key — per-context proxy decouples Worker from Proxy.
 * browserPath is NOT part of the key — same browser type typically uses same path.
 */
public record WorkerKey(String browserType, boolean headless, String userDataDir) {
}
