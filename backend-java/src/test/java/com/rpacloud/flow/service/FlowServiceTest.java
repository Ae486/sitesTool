package com.rpacloud.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.execution.process.AutomationExecutor;
import com.rpacloud.execution.schedule.FlowScheduleService;
import com.rpacloud.flow.dto.FlowCreateRequest;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.site.entity.Site;
import com.rpacloud.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

    @Mock private FlowRepository flowRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private FlowScheduleService flowScheduleService;
    @Mock private AutomationExecutor automationExecutor;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private FlowService flowService;

    @Test
    void createFlowSerializesDslToString() {
        Site site = Site.builder().id(1L).name("Test").url("https://example.com").build();
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));

        Map<String, Object> dsl = Map.of("steps", java.util.List.of(
                Map.of("type", "navigate", "url", "https://example.com")
        ));

        AutomationFlow saved = AutomationFlow.builder()
                .id(1L).siteId(1L).site(site).name("Test Flow")
                .dsl("{\"steps\":[{\"type\":\"navigate\",\"url\":\"https://example.com\"}]}")
                .lastStatus(FlowStatus.idle).headless(true).browserType("chromium")
                .useCdpMode(false).cdpPort(9222).isActive(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(flowRepository.save(any())).thenReturn(saved);

        FlowCreateRequest req = new FlowCreateRequest();
        req.setSiteId(1L);
        req.setName("Test Flow");
        req.setDsl(dsl);

        FlowResponse resp = flowService.create(req);

        // DSL should be deserialized back to a Map (not a String)
        assertThat(resp.getDsl()).isInstanceOf(Map.class);
        assertThat(resp.getDsl()).containsKey("steps");
    }

    @Test
    void getByIdThrows404WhenNotFound() {
        when(flowRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flowService.getById(999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void dslRoundTrip() throws Exception {
        // Entity stores DSL as JSON string
        String dslJson = "{\"steps\":[{\"type\":\"click\",\"selector\":\"#btn\"}]}";
        AutomationFlow flow = AutomationFlow.builder()
                .id(1L).siteId(1L).name("Test").dsl(dslJson)
                .lastStatus(FlowStatus.idle).headless(true).browserType("chromium")
                .useCdpMode(false).cdpPort(9222).isActive(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));

        FlowResponse resp = flowService.getById(1L);

        // Response should have DSL as Map (JSON object)
        assertThat(resp.getDsl()).containsKey("steps");
        @SuppressWarnings("unchecked")
        var steps = (java.util.List<Map<String, Object>>) resp.getDsl().get("steps");
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0)).containsEntry("type", "click");
    }
}
