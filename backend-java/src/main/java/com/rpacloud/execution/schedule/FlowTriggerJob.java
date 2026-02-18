package com.rpacloud.execution.schedule;

import com.rpacloud.execution.process.AutomationExecutor;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.repository.FlowRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
public class FlowTriggerJob extends QuartzJobBean {

    private final FlowRepository flowRepository;
    private final AutomationExecutor automationExecutor;

    public FlowTriggerJob(FlowRepository flowRepository, AutomationExecutor automationExecutor) {
        this.flowRepository = flowRepository;
        this.automationExecutor = automationExecutor;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        long flowId = context.getJobDetail().getJobDataMap().getLong("flowId");
        log.info("Quartz triggered flow {}", flowId);

        AutomationFlow flow = flowRepository.findById(flowId).orElse(null);
        if (flow == null) {
            log.warn("Flow {} not found, skipping scheduled trigger", flowId);
            return;
        }
        if (!flow.getIsActive()) {
            log.info("Flow {} is inactive, skipping scheduled trigger", flowId);
            return;
        }
        if (automationExecutor.isRunning(flowId)) {
            log.info("Flow {} is already running, skipping scheduled trigger", flowId);
            return;
        }

        try {
            automationExecutor.trigger(flow);
            log.info("Scheduled trigger for flow {} succeeded", flowId);
        } catch (Exception e) {
            log.error("Scheduled trigger for flow {} failed: {}", flowId, e.getMessage());
        }
    }
}
