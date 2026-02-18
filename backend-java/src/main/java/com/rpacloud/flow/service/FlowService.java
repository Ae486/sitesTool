package com.rpacloud.flow.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.execution.process.AutomationExecutor;
import com.rpacloud.flow.dto.FlowCreateRequest;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.dto.FlowUpdateRequest;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.execution.schedule.FlowScheduleService;
import com.rpacloud.site.entity.Site;
import com.rpacloud.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import com.rpacloud.common.dto.OffsetBasedPageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlowService {

    private final FlowRepository flowRepository;
    private final SiteRepository siteRepository;
    private final ObjectMapper objectMapper;
    private final FlowScheduleService flowScheduleService;
    private final AutomationExecutor automationExecutor;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Transactional(readOnly = true)
    public PageResponse<FlowResponse> list(int skip, int limit) {
        Page<AutomationFlow> page = flowRepository.findAll(new OffsetBasedPageRequest(skip, limit));
        List<FlowResponse> items = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(page.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public FlowResponse getById(Long id) {
        AutomationFlow flow = flowRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found"));
        return toResponse(flow);
    }

    @Transactional
    public FlowResponse create(FlowCreateRequest request) {
        Site site = siteRepository.findById(request.getSiteId())
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Site not found"));
        AutomationFlow flow = AutomationFlow.builder()
                .site(site)
                .name(request.getName())
                .description(request.getDescription())
                .cronExpression(request.getCronExpression())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .dsl(serializeDsl(request.getDsl()))
                .headless(request.getHeadless() != null ? request.getHeadless() : true)
                .browserType(request.getBrowserType() != null ? request.getBrowserType() : "chromium")
                .browserPath(request.getBrowserPath())
                .useCdpMode(request.getUseCdpMode() != null ? request.getUseCdpMode() : false)
                .cdpPort(request.getCdpPort() != null ? request.getCdpPort() : 9222)
                .cdpUserDataDir(request.getCdpUserDataDir())
                .useProxy(request.getUseProxy() != null ? request.getUseProxy() : false)
                .proxyId(request.getProxyId())
                .build();
        flow = flowRepository.save(flow);
        flowScheduleService.syncSchedule(flow.getId(), flow.getCronExpression(), flow.getIsActive());
        return toResponse(flow);
    }

    @Transactional
    public FlowResponse update(Long id, FlowUpdateRequest request) {
        AutomationFlow flow = flowRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found"));
        if (request.getName() != null) flow.setName(request.getName());
        if (request.getDescription() != null) flow.setDescription(request.getDescription());
        if (request.getCronExpression() != null) flow.setCronExpression(request.getCronExpression());
        if (request.getIsActive() != null) flow.setIsActive(request.getIsActive());
        if (request.getDsl() != null) flow.setDsl(serializeDsl(request.getDsl()));
        if (request.getHeadless() != null) flow.setHeadless(request.getHeadless());
        if (request.getBrowserType() != null) flow.setBrowserType(request.getBrowserType());
        if (request.getBrowserPath() != null) flow.setBrowserPath(request.getBrowserPath());
        if (request.getUseCdpMode() != null) flow.setUseCdpMode(request.getUseCdpMode());
        if (request.getCdpPort() != null) flow.setCdpPort(request.getCdpPort());
        if (request.getCdpUserDataDir() != null) flow.setCdpUserDataDir(request.getCdpUserDataDir());
        if (request.getUseProxy() != null) flow.setUseProxy(request.getUseProxy());
        if (request.getProxyId() != null) flow.setProxyId(request.getProxyId());
        flow = flowRepository.save(flow);
        flowScheduleService.syncSchedule(flow.getId(), flow.getCronExpression(), flow.getIsActive());
        return toResponse(flow);
    }

    @Transactional
    public void delete(Long id) {
        if (!flowRepository.existsById(id)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found");
        }
        if (automationExecutor.isRunning(id)) {
            throw new BizException(ErrorCode.FLOW_ALREADY_RUNNING, "Cannot delete a running flow");
        }
        flowScheduleService.removeSchedule(id);
        flowRepository.deleteById(id);
    }

    public AutomationFlow getEntityById(Long id) {
        return flowRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found"));
    }

    private FlowResponse toResponse(AutomationFlow flow) {
        return FlowResponse.builder()
                .id(flow.getId())
                .siteId(flow.getSiteId())
                .name(flow.getName())
                .description(flow.getDescription())
                .cronExpression(flow.getCronExpression())
                .isActive(flow.getIsActive())
                .dsl(deserializeDsl(flow.getDsl()))
                .lastStatus(flow.getLastStatus())
                .headless(flow.getHeadless())
                .browserType(flow.getBrowserType())
                .browserPath(flow.getBrowserPath())
                .useCdpMode(flow.getUseCdpMode())
                .cdpPort(flow.getCdpPort())
                .cdpUserDataDir(flow.getCdpUserDataDir())
                .useProxy(flow.getUseProxy())
                .proxyId(flow.getProxyId())
                .createdAt(flow.getCreatedAt())
                .updatedAt(flow.getUpdatedAt())
                .build();
    }

    private String serializeDsl(Map<String, Object> dsl) {
        try {
            return objectMapper.writeValueAsString(dsl);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Invalid DSL format");
        }
    }

    private Map<String, Object> deserializeDsl(String dsl) {
        if (dsl == null || dsl.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(dsl, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    // --- Execution operations (moved from Controller) ---

    public AutomationExecutor.TriggerResult triggerFlow(Long id) {
        AutomationFlow flow = getEntityById(id);
        return automationExecutor.trigger(flow);
    }

    public AutomationExecutor.TriggerResult stopFlow(Long id) {
        AutomationFlow flow = getEntityById(id);
        return automationExecutor.stop(flow);
    }

    public boolean isRunning(Long id) {
        getById(id); // verify exists
        return automationExecutor.isRunning(id);
    }

    public java.util.List<Long> getRunningFlowIds() {
        return automationExecutor.getRunningFlowIds();
    }
}
