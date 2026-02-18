package com.rpacloud.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.history.dto.HistoryResponse;
import com.rpacloud.history.entity.CheckinHistory;
import com.rpacloud.history.repository.HistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock private HistoryRepository historyRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private HistoryService historyService;

    @Test
    void executionIdExtractedFromResultPayload() {
        CheckinHistory h = CheckinHistory.builder()
                .id(1L).flowId(1L).status(FlowStatus.success)
                .startedAt(LocalDateTime.now())
                .resultPayload("{\"execution_id\":\"exec-123\",\"step_results\":[]}")
                .screenshotFiles(List.of())
                .errorTypes(List.of())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(historyRepository.findById(1L)).thenReturn(Optional.of(h));

        HistoryResponse resp = historyService.getById(1L);
        assertThat(resp.getExecutionId()).isEqualTo("exec-123");
    }

    @Test
    void primaryErrorTypeFromErrorTypesList() {
        CheckinHistory h = CheckinHistory.builder()
                .id(1L).flowId(1L).status(FlowStatus.failed)
                .startedAt(LocalDateTime.now())
                .errorTypes(List.of("TIMEOUT_ERROR", "SELECTOR_ERROR"))
                .screenshotFiles(List.of())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(historyRepository.findById(1L)).thenReturn(Optional.of(h));

        HistoryResponse resp = historyService.getById(1L);
        assertThat(resp.getPrimaryErrorType()).isEqualTo("TIMEOUT_ERROR");
    }

    @Test
    void failedStepSummaryExtractedFromStepResults() {
        String payload = """
                {
                  "execution_id": "exec-1",
                  "step_results": [
                    {"step_index": 0, "step_type": "navigate", "success": true},
                    {"step_index": 1, "step_type": "click", "description": "点击登录按钮", "success": false, "error": "[TIMEOUT] 等待元素超时"}
                  ]
                }
                """;
        CheckinHistory h = CheckinHistory.builder()
                .id(1L).flowId(1L).status(FlowStatus.failed)
                .startedAt(LocalDateTime.now())
                .resultPayload(payload)
                .errorTypes(List.of())
                .screenshotFiles(List.of())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(historyRepository.findById(1L)).thenReturn(Optional.of(h));

        HistoryResponse resp = historyService.getById(1L);
        assertThat(resp.getFailedStepSummary()).contains("步骤 2");
        assertThat(resp.getFailedStepSummary()).contains("点击登录按钮");
        assertThat(resp.getPrimaryErrorType()).isEqualTo("TIMEOUT");
    }

    @Test
    void fallbackToErrorMessageWhenNoStepResults() {
        CheckinHistory h = CheckinHistory.builder()
                .id(1L).flowId(1L).status(FlowStatus.failed)
                .startedAt(LocalDateTime.now())
                .errorMessage("Browser crashed\nwith details")
                .errorTypes(List.of())
                .screenshotFiles(List.of())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(historyRepository.findById(1L)).thenReturn(Optional.of(h));

        HistoryResponse resp = historyService.getById(1L);
        assertThat(resp.getFailedStepSummary()).isEqualTo("Browser crashed");
    }
}
