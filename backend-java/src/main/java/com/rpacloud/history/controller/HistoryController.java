package com.rpacloud.history.controller;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.flow.service.FlowService;
import com.rpacloud.history.dto.HistoryResponse;
import com.rpacloud.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;
    private final FlowService flowService;

    @GetMapping("/history")
    public PageResponse<HistoryResponse> list(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(name = "error_type", required = false) String errorType) {
        return historyService.list(skip, limit, errorType);
    }

    @GetMapping("/history/{id}")
    public HistoryResponse getById(@PathVariable Long id) {
        return historyService.getById(id);
    }

    @DeleteMapping("/history/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        historyService.delete(id);
    }

    @GetMapping("/flows/{flowId}/history")
    public PageResponse<HistoryResponse> listByFlow(
            @PathVariable Long flowId,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(name = "error_type", required = false) String errorType) {
        flowService.getById(flowId); // verify flow exists
        return historyService.listByFlow(flowId, skip, limit, errorType);
    }
}
