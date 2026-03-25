package com.rpacloud.execution.worker;

/**
 * Strategy interface for creating Workers.
 * Tests mock this; Phase 2+ implementation spawns real subprocesses.
 */
@FunctionalInterface
public interface WorkerFactory {
    WorkerHandle create(WorkerKey key);
}
