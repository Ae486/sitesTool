package com.rpacloud.history.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.dto.OffsetBasedPageRequest;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.history.dto.HistoryResponse;
import com.rpacloud.history.entity.CheckinHistory;
import com.rpacloud.history.repository.HistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<HistoryResponse> list(int skip, int limit, String errorType) {
        var pageable = new OffsetBasedPageRequest(skip, limit);
        Page<CheckinHistory> page;
        if (errorType != null && !errorType.isBlank()) {
            String pattern = "\"" + errorType + "\"";
            page = historyRepository.findByErrorTypeContaining(pattern, pageable);
        } else {
            page = historyRepository.findAllByOrderByStartedAtDesc(pageable);
        }
        List<HistoryResponse> items = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(page.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryResponse> listByFlow(Long flowId, int skip, int limit, String errorType) {
        var pageable = new OffsetBasedPageRequest(skip, limit);
        Page<CheckinHistory> page;
        if (errorType != null && !errorType.isBlank()) {
            String pattern = "\"" + errorType + "\"";
            page = historyRepository.findByFlowIdAndErrorTypeContaining(flowId, pattern, pageable);
        } else {
            page = historyRepository.findByFlowIdOrderByStartedAtDesc(flowId, pageable);
        }
        List<HistoryResponse> items = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(page.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public HistoryResponse getById(Long id) {
        CheckinHistory history = historyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "History not found"));
        return toResponse(history);
    }

    @Transactional
    public void delete(Long id) {
        if (!historyRepository.existsById(id)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "History not found");
        }
        historyRepository.deleteById(id);
    }

    private HistoryResponse toResponse(CheckinHistory h) {
        String executionId = null;
        String primaryErrorType = null;
        String failedStepSummary = null;

        // Compute observability fields (port of Python history_observability.py)
        Map<String, Object> payload = parsePayload(h.getResultPayload());

        if (payload != null) {
            Object rawExecId = payload.get("execution_id");
            if (rawExecId instanceof String s && !s.isBlank()) {
                executionId = s;
            }
        }

        List<String> errorTypes = h.getErrorTypes();
        if (errorTypes != null) {
            for (String t : errorTypes) {
                if (t != null && !t.isBlank()) {
                    primaryErrorType = t;
                    break;
                }
            }
        }

        if (payload != null) {
            Object rawStepResults = payload.get("step_results");
            if (rawStepResults instanceof List<?> stepResults) {
                for (Object item : stepResults) {
                    if (!(item instanceof Map<?, ?> step)) continue;
                    if (Boolean.TRUE.equals(step.get("success"))) continue;

                    Object idx = step.get("step_index");
                    Integer stepNum = (idx instanceof Number n) ? n.intValue() + 1 : null;
                    String stepType = step.get("step_type") instanceof String s ? s : null;
                    String desc = step.get("description") instanceof String s ? s : null;
                    String err = step.get("error") instanceof String s ? s : null;

                    String tag = null;
                    String errMain = null;
                    if (err != null) {
                        errMain = err.split("\n")[0].trim();
                        if (errMain.startsWith("[") && errMain.contains("]")) {
                            tag = errMain.substring(1, errMain.indexOf(']'));
                            String rest = errMain.substring(errMain.indexOf(']') + 1).trim();
                            errMain = rest.isEmpty() ? errMain : rest;
                        }
                    }

                    if (primaryErrorType == null && tag != null) {
                        primaryErrorType = tag;
                    }

                    StringBuilder sb = new StringBuilder();
                    if (stepNum != null) sb.append("步骤 ").append(stepNum);
                    if (desc != null) {
                        if (!sb.isEmpty()) sb.append(" - ");
                        sb.append(desc);
                    } else if (stepType != null) {
                        if (!sb.isEmpty()) sb.append(" - ");
                        sb.append(stepType);
                    }
                    if (tag != null) {
                        if (!sb.isEmpty()) sb.append(" - ");
                        sb.append(tag);
                    }
                    if (errMain != null) {
                        if (!sb.isEmpty()) sb.append(" - ");
                        sb.append(errMain);
                    }

                    if (!sb.isEmpty()) {
                        failedStepSummary = sb.toString();
                    }
                    break;
                }
            }
        }

        if (failedStepSummary == null && h.getErrorMessage() != null) {
            String firstLine = h.getErrorMessage().split("\n")[0].trim();
            if (!firstLine.isEmpty()) failedStepSummary = firstLine;
        }

        return HistoryResponse.builder()
                .id(h.getId())
                .flowId(h.getFlowId())
                .status(h.getStatus())
                .startedAt(h.getStartedAt())
                .finishedAt(h.getFinishedAt())
                .durationMs(h.getDurationMs())
                .log(h.getLog())
                .resultPayload(h.getResultPayload())
                .errorMessage(h.getErrorMessage())
                .screenshotFiles(h.getScreenshotFiles() != null ? h.getScreenshotFiles() : List.of())
                .errorTypes(h.getErrorTypes() != null ? h.getErrorTypes() : List.of())
                .executionId(executionId)
                .primaryErrorType(primaryErrorType)
                .failedStepSummary(failedStepSummary)
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
