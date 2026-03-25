package com.rpacloud.flow.controller;

import java.util.List;
import java.util.Map;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.flow.dto.FlowCreateRequest;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.dto.FlowUpdateRequest;
import com.rpacloud.flow.service.FlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;

    @GetMapping
    public PageResponse<FlowResponse> list(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return flowService.list(skip, limit);
    }

    @GetMapping("/{id}")
    public FlowResponse getById(@PathVariable Long id) {
        return flowService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlowResponse create(@Valid @RequestBody FlowCreateRequest request) {
        return flowService.create(request);
    }

    @PutMapping("/{id}")
    public FlowResponse update(@PathVariable Long id, @RequestBody FlowUpdateRequest request) {
        return flowService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        flowService.delete(id);
    }

    @PostMapping("/{id}/trigger")
    public Map<String, String> trigger(@PathVariable Long id) {
        var result = flowService.triggerFlow(id);
        return Map.of("status", result.status(), "message", result.message(),
                "execution_id", result.executionId() != null ? result.executionId() : "");
    }

    @PostMapping("/{id}/stop")
    public Map<String, String> stop(@PathVariable Long id) {
        var result = flowService.stopFlow(id);
        return Map.of("status", result.status(), "message", result.message());
    }

    @GetMapping("/{id}/status")
    public Map<String, Boolean> status(@PathVariable Long id) {
        return Map.of("is_running", flowService.isRunning(id));
    }

    @GetMapping("/running/list")
    public Map<String, List<Long>> runningList() {
        return Map.of("running_flows", flowService.getRunningFlowIds());
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportFlow(@PathVariable Long id) {
        byte[] json = flowService.exportFlow(id);
        FlowResponse flow = flowService.getById(id);
        String filename = "flow-" + flow.getName().replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public FlowResponse importFlow(@RequestParam("file") MultipartFile file) {
        return flowService.importFlow(file);
    }
}
