package com.rpacloud.history.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.rpacloud.flow.entity.FlowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class HistoryResponse {
    private Long id;
    private Long flowId;
    private FlowStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer durationMs;
    private String log;
    private String resultPayload;
    private String errorMessage;
    private List<String> screenshotFiles;
    private List<String> errorTypes;
    private String executionId;
    private String primaryErrorType;
    private String failedStepSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
