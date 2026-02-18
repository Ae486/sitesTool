package com.rpacloud.history.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.service.FlowService;
import com.rpacloud.history.dto.HistoryResponse;
import com.rpacloud.history.service.HistoryService;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class HistoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private HistoryService historyService;
    @MockBean private FlowService flowService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    private HistoryResponse sampleHistory() {
        return HistoryResponse.builder()
                .id(1L).flowId(1L).status(FlowStatus.success)
                .startedAt(LocalDateTime.of(2025, 1, 1, 8, 0))
                .finishedAt(LocalDateTime.of(2025, 1, 1, 8, 0, 30))
                .durationMs(30000)
                .errorTypes(List.of()).screenshotFiles(List.of())
                .executionId("exec-abc").primaryErrorType(null)
                .createdAt(LocalDateTime.of(2025, 1, 1, 8, 0))
                .updatedAt(LocalDateTime.of(2025, 1, 1, 8, 0, 30))
                .build();
    }

    @Test
    void listHistory_returnsPaginated() throws Exception {
        when(historyService.list(0, 50, null))
                .thenReturn(PageResponse.of(1L, List.of(sampleHistory())));

        mockMvc.perform(get("/api/history").param("skip", "0").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].flow_id").value(1))
                .andExpect(jsonPath("$.items[0].status").value("success"))
                .andExpect(jsonPath("$.items[0].duration_ms").value(30000))
                .andExpect(jsonPath("$.items[0].execution_id").value("exec-abc"));
    }

    @Test
    void listHistory_withErrorTypeFilter() throws Exception {
        when(historyService.list(0, 50, "TIMEOUT"))
                .thenReturn(PageResponse.of(0L, List.of()));

        mockMvc.perform(get("/api/history")
                        .param("skip", "0").param("limit", "50").param("error_type", "TIMEOUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void getHistoryById() throws Exception {
        when(historyService.getById(1L)).thenReturn(sampleHistory());

        mockMvc.perform(get("/api/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.execution_id").value("exec-abc"));
    }

    @Test
    void deleteHistory_returns204() throws Exception {
        mockMvc.perform(delete("/api/history/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void listByFlow_returnsPaginated() throws Exception {
        when(flowService.getById(1L)).thenReturn(FlowResponse.builder().id(1L).build());
        when(historyService.listByFlow(1L, 0, 20, null))
                .thenReturn(PageResponse.of(1L, List.of(sampleHistory())));

        mockMvc.perform(get("/api/flows/1/history").param("skip", "0").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].flow_id").value(1));
    }
}
