package com.rpacloud.execution.schedule;

import static org.mockito.Mockito.*;

import com.rpacloud.execution.process.AutomationExecutor;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.repository.FlowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class FlowTriggerJobTest {

    @Mock private FlowRepository flowRepository;
    @Mock private AutomationExecutor automationExecutor;
    @Mock private JobExecutionContext jobContext;
    @Mock private JobDetail jobDetail;
    @InjectMocks private FlowTriggerJob flowTriggerJob;

    @Test
    void executesFlowWhenActiveAndNotRunning() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.putAsString("flowId", 1L);
        when(jobContext.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);

        AutomationFlow flow = AutomationFlow.builder().id(1L).isActive(true).build();
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));
        when(automationExecutor.isRunning(1L)).thenReturn(false);

        flowTriggerJob.executeInternal(jobContext);

        verify(automationExecutor).trigger(flow);
    }

    @Test
    void skipsWhenFlowNotFound() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.putAsString("flowId", 999L);
        when(jobContext.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);
        when(flowRepository.findById(999L)).thenReturn(Optional.empty());

        flowTriggerJob.executeInternal(jobContext);

        verifyNoInteractions(automationExecutor);
    }

    @Test
    void skipsWhenFlowInactive() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.putAsString("flowId", 1L);
        when(jobContext.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);

        AutomationFlow flow = AutomationFlow.builder().id(1L).isActive(false).build();
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));

        flowTriggerJob.executeInternal(jobContext);

        verify(automationExecutor, never()).trigger(any());
    }

    @Test
    void skipsWhenAlreadyRunning() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.putAsString("flowId", 1L);
        when(jobContext.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);

        AutomationFlow flow = AutomationFlow.builder().id(1L).isActive(true).build();
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));
        when(automationExecutor.isRunning(1L)).thenReturn(true);

        flowTriggerJob.executeInternal(jobContext);

        verify(automationExecutor, never()).trigger(any());
    }
}
