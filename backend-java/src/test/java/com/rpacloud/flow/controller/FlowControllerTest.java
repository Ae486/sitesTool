package com.rpacloud.flow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.execution.process.AutomationExecutor;
import com.rpacloud.flow.dto.FlowCreateRequest;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.service.FlowService;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(FlowController.class)
@AutoConfigureMockMvc(addFilters = false)
class FlowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private FlowService flowService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    private FlowResponse sampleFlow() {
        return FlowResponse.builder()
                .id(1L).siteId(1L).name("Test Flow")
                .dsl(Map.of("steps", List.of(Map.of("type", "navigate", "url", "https://example.com"))))
                .lastStatus(FlowStatus.idle).isActive(true)
                .headless(true).browserType("chromium").useCdpMode(false).cdpPort(9222)
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
    }

    @Test
    void listFlows_returnsPaginatedResponse() throws Exception {
        when(flowService.list(0, 20))
                .thenReturn(PageResponse.of(1L, List.of(sampleFlow())));

        mockMvc.perform(get("/api/flows").param("skip", "0").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].dsl").isMap())
                .andExpect(jsonPath("$.items[0].last_status").value("idle"))
                .andExpect(jsonPath("$.items[0].is_active").value(true))
                .andExpect(jsonPath("$.items[0].created_at").isString());
    }

    @Test
    void getById_returnsFlow() throws Exception {
        when(flowService.getById(1L)).thenReturn(sampleFlow());

        mockMvc.perform(get("/api/flows/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Flow"))
                .andExpect(jsonPath("$.dsl.steps").isArray());
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(flowService.getById(999L))
                .thenThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found"));

        mockMvc.perform(get("/api/flows/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createFlow_returns201() throws Exception {
        when(flowService.create(any(FlowCreateRequest.class))).thenReturn(sampleFlow());

        FlowCreateRequest req = new FlowCreateRequest();
        req.setSiteId(1L);
        req.setName("Test Flow");
        req.setDsl(Map.of("steps", List.of()));

        mockMvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void triggerFlow_returnsStarted() throws Exception {
        when(flowService.triggerFlow(1L))
                .thenReturn(new AutomationExecutor.TriggerResult("started", "Flow execution started", "exec-123"));

        mockMvc.perform(post("/api/flows/1/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"))
                .andExpect(jsonPath("$.execution_id").value("exec-123"));
    }

    @Test
    void statusEndpoint_returnsIsRunning() throws Exception {
        when(flowService.isRunning(1L)).thenReturn(false);

        mockMvc.perform(get("/api/flows/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_running").value(false));
    }

    @Test
    void runningList_returnsEmptyArray() throws Exception {
        when(flowService.getRunningFlowIds()).thenReturn(List.of());

        mockMvc.perform(get("/api/flows/running/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running_flows").isArray())
                .andExpect(jsonPath("$.running_flows").isEmpty());
    }

    // --- Export/Import controller tests ---

    @Test
    void exportFlow_returnsJsonAttachment() throws Exception {
        byte[] exportedJson = "{\"format_version\":1,\"name\":\"Test\"}".getBytes();
        when(flowService.exportFlow(1L)).thenReturn(exportedJson);
        when(flowService.getById(1L)).thenReturn(sampleFlow());

        mockMvc.perform(get("/api/flows/1/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"format_version\":1,\"name\":\"Test\"}"));
    }

    @Test
    void importFlow_returns201() throws Exception {
        FlowResponse imported = FlowResponse.builder()
                .id(10L).siteId(1L).name("Imported (imported)")
                .dsl(Map.of("steps", List.of())).lastStatus(FlowStatus.idle).isActive(true)
                .headless(true).browserType("chromium").useCdpMode(false).cdpPort(9222)
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
        when(flowService.importFlow(any(MultipartFile.class))).thenReturn(imported);

        MockMultipartFile file = new MockMultipartFile("file", "flow.json", "application/json",
                "{\"format_version\":1,\"name\":\"Test\",\"dsl\":{\"steps\":[]}}".getBytes());

        mockMvc.perform(multipart("/api/flows/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Imported (imported)"));
    }
}
