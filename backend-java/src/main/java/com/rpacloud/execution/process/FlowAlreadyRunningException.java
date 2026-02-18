package com.rpacloud.execution.process;

public class FlowAlreadyRunningException extends RuntimeException {
    private final long flowId;

    public FlowAlreadyRunningException(long flowId) {
        super("Flow " + flowId + " is already running");
        this.flowId = flowId;
    }

    public long getFlowId() { return flowId; }
}
