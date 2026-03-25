package com.rpacloud.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;

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

    // --- Export tests ---

    @Test
    void exportFlow_returnsValidJson() throws Exception {
        AutomationFlow flow = AutomationFlow.builder()
                .id(1L).name("Daily Checkin").description("Auto checkin")
                .dsl("{\"steps\":[{\"type\":\"navigate\",\"url\":\"https://example.com\"}]}")
                .cronExpression("0 8 * * *").build();
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));

        byte[] result = flowService.exportFlow(1L);
        Map<String, Object> exported = objectMapper.readValue(result, Map.class);

        assertThat(exported.get("format_version")).isEqualTo(1);
        assertThat(exported.get("name")).isEqualTo("Daily Checkin");
        assertThat(exported.get("description")).isEqualTo("Auto checkin");
        assertThat(exported.get("cron_expression")).isEqualTo("0 8 * * *");
        assertThat(exported.get("exported_at")).isNotNull();
        assertThat(exported.get("dsl")).isInstanceOf(Map.class);
    }

    @Test
    void exportFlow_notFoundThrows() {
        when(flowRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flowService.exportFlow(99L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // --- Import tests ---

    @Test
    void importFlow_success() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "format_version", 1,
                "name", "Imported Flow",
                "description", "desc",
                "dsl", Map.of("steps", List.of(Map.of("type", "navigate", "url", "https://example.com"))),
                "cron_expression", "0 9 * * *"
        ));
        MockMultipartFile file = new MockMultipartFile("file", "flow.json", "application/json", json.getBytes());

        Site site = Site.builder().id(1L).name("Test").url("https://example.com").build();
        when(siteRepository.findAll(any(com.rpacloud.common.dto.OffsetBasedPageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(site)));
        when(flowRepository.save(any())).thenAnswer(inv -> {
            AutomationFlow f = inv.getArgument(0);
            f.setId(10L);
            f.setLastStatus(FlowStatus.idle);
            f.setHeadless(true);
            f.setBrowserType("chromium");
            f.setUseCdpMode(false);
            f.setCdpPort(9222);
            f.setIsActive(true);
            f.setCreatedAt(LocalDateTime.now());
            f.setUpdatedAt(LocalDateTime.now());
            return f;
        });

        FlowResponse resp = flowService.importFlow(file);

        assertThat(resp.getName()).isEqualTo("Imported Flow (imported)");
        assertThat(resp.getDsl()).containsKey("steps");
    }

    @Test
    void importFlow_emptyFileThrows() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.json", "application/json", new byte[0]);

        assertThatThrownBy(() -> flowService.importFlow(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void importFlow_wrongFormatVersionThrows() {
        String json = "{\"format_version\":2,\"name\":\"Test\",\"dsl\":{\"steps\":[]}}";
        MockMultipartFile file = new MockMultipartFile("file", "flow.json", "application/json", json.getBytes());

        assertThatThrownBy(() -> flowService.importFlow(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("format_version");
    }

    @Test
    void importFlow_missingNameThrows() {
        String json = "{\"format_version\":1,\"dsl\":{\"steps\":[]}}";
        MockMultipartFile file = new MockMultipartFile("file", "flow.json", "application/json", json.getBytes());

        assertThatThrownBy(() -> flowService.importFlow(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("name");
    }

    @Test
    void importFlow_missingDslStepsThrows() {
        String json = "{\"format_version\":1,\"name\":\"Test\",\"dsl\":{}}";
        MockMultipartFile file = new MockMultipartFile("file", "flow.json", "application/json", json.getBytes());

        assertThatThrownBy(() -> flowService.importFlow(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("steps");
    }

    @Test
    void importFlow_noSiteAvailableThrows() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "format_version", 1, "name", "Test",
                "dsl", Map.of("steps", List.of())
        ));
        MockMultipartFile file = new MockMultipartFile("file", "flow.json", "application/json", json.getBytes());

        when(siteRepository.findAll(any(com.rpacloud.common.dto.OffsetBasedPageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThatThrownBy(() -> flowService.importFlow(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("No site available");
    }
}
