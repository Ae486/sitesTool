package com.rpacloud.execution.worker;

public class WorkerAcquireTimeoutException extends RuntimeException {

    public WorkerAcquireTimeoutException(String message) {
        super(message);
    }
}
