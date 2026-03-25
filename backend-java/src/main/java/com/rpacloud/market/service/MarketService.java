package com.rpacloud.market.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.dto.OffsetBasedPageRequest;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.market.dto.MarketFlowDetailResponse;
import com.rpacloud.market.dto.MarketFlowResponse;
import com.rpacloud.market.dto.MarketPublishRequest;
import com.rpacloud.market.entity.MarketplaceFlow;
import com.rpacloud.market.repository.MarketplaceFlowRepository;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "cookie", "cookies", "token", "api_key", "apikey", "secret", "credentials"
    );

    private final MarketplaceFlowRepository marketRepository;
    private final FlowRepository flowRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<MarketFlowResponse> list(int skip, int limit) {
        Page<MarketplaceFlow> page = marketRepository.findByIsActiveTrueAndVisibility(
                "PUBLIC", new OffsetBasedPageRequest(skip, limit));
        List<MarketplaceFlow> content = page.getContent();
        Map<Long, String> authorNames = resolveAuthorNames(content);
        List<MarketFlowResponse> items = content.stream()
                .map(mf -> toListResponse(mf, authorNames.getOrDefault(mf.getAuthorId(), "Unknown")))
                .toList();
        return PageResponse.of(page.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public MarketFlowDetailResponse getById(Long id) {
        MarketplaceFlow mf = marketRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Marketplace flow not found"));
        return toDetailResponse(mf);
    }

    @Transactional
    public MarketFlowResponse publish(MarketPublishRequest request, Long authorId) {
        AutomationFlow flow = flowRepository.findById(request.getFlowId())
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found"));

        marketRepository.findByFlowIdAndAuthorIdAndIsActiveTrue(request.getFlowId(), authorId)
                .ifPresent(existing -> {
                    throw new BizException(ErrorCode.DUPLICATE_RESOURCE, "Flow already published");
                });

        String sanitizedDsl = sanitizeDsl(flow.getDsl());

        MarketplaceFlow mf = MarketplaceFlow.builder()
                .authorId(authorId)
                .flowId(flow.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .visibility(request.getVisibility() != null ? request.getVisibility() : "PUBLIC")
                .dslSnapshot(sanitizedDsl)
                .build();
        mf = marketRepository.save(mf);
        String authorName = userRepository.findById(authorId).map(User::getFullName).orElse("Unknown");
        log.info("Flow published: marketId={}, flowId={}, authorId={}", mf.getId(), flow.getId(), authorId);
        return toListResponse(mf, authorName);
    }

    @Transactional
    public FlowResponse install(Long marketFlowId, Long userId) {
        MarketplaceFlow mf = marketRepository.findByIdAndIsActiveTrue(marketFlowId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Marketplace flow not found"));

        // Find any site for the user to associate with (use first available)
        // In a real multi-tenant setup, this would be more sophisticated
        AutomationFlow sourceFlow = flowRepository.findById(mf.getFlowId()).orElse(null);
        Long siteId = sourceFlow != null ? sourceFlow.getSiteId() : null;

        if (siteId == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED,
                    "Cannot install: source flow's site no longer exists");
        }

        AutomationFlow installed = AutomationFlow.builder()
                .site(sourceFlow.getSite())
                .name(mf.getTitle() + " (installed)")
                .description(mf.getDescription())
                .dsl(mf.getDslSnapshot())
                .build();
        installed = flowRepository.save(installed);

        marketRepository.incrementDownloadCount(mf.getId());

        log.info("Flow installed: marketId={}, newFlowId={}, userId={}", marketFlowId, installed.getId(), userId);
        return FlowResponse.builder()
                .id(installed.getId())
                .siteId(installed.getSiteId())
                .name(installed.getName())
                .description(installed.getDescription())
                .dsl(deserializeDsl(installed.getDsl()))
                .isActive(installed.getIsActive())
                .lastStatus(installed.getLastStatus())
                .createdAt(installed.getCreatedAt())
                .updatedAt(installed.getUpdatedAt())
                .build();
    }

    @Transactional
    public void unpublish(Long marketFlowId, Long authorId) {
        MarketplaceFlow mf = marketRepository.findByIdAndIsActiveTrue(marketFlowId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Marketplace flow not found"));
        if (!mf.getAuthorId().equals(authorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Only the author can unpublish");
        }
        mf.setIsActive(false);
        marketRepository.save(mf);
        log.info("Flow unpublished: marketId={}, authorId={}", marketFlowId, authorId);
    }

    private String sanitizeDsl(String dslString) {
        if (dslString == null || dslString.isBlank()) return "{}";
        try {
            Map<String, Object> dsl = objectMapper.readValue(dslString, MAP_TYPE);
            sanitizeMap(dsl);
            return objectMapper.writeValueAsString(dsl);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "DSL is not valid JSON, cannot sanitize");
        }
    }

    @SuppressWarnings("unchecked")
    private void sanitizeMap(Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            if (SENSITIVE_KEYS.contains(entry.getKey().toLowerCase())) {
                entry.setValue("");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                sanitizeMap((Map<String, Object>) nested);
            } else if (entry.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem) {
                        sanitizeMap((Map<String, Object>) nestedItem);
                    }
                }
            }
        }
    }

    private MarketFlowResponse toListResponse(MarketplaceFlow mf, String authorName) {
        return MarketFlowResponse.builder()
                .id(mf.getId())
                .title(mf.getTitle())
                .description(mf.getDescription())
                .authorName(authorName)
                .version(mf.getVersion())
                .visibility(mf.getVisibility())
                .downloadCount(mf.getDownloadCount())
                .avgRating(mf.getAvgRating())
                .createdAt(mf.getCreatedAt())
                .updatedAt(mf.getUpdatedAt())
                .build();
    }

    private MarketFlowDetailResponse toDetailResponse(MarketplaceFlow mf) {
        String authorName = userRepository.findById(mf.getAuthorId())
                .map(User::getFullName)
                .orElse("Unknown");
        return MarketFlowDetailResponse.builder()
                .id(mf.getId())
                .title(mf.getTitle())
                .description(mf.getDescription())
                .authorName(authorName)
                .version(mf.getVersion())
                .visibility(mf.getVisibility())
                .downloadCount(mf.getDownloadCount())
                .avgRating(mf.getAvgRating())
                .dslSnapshot(deserializeDsl(mf.getDslSnapshot()))
                .createdAt(mf.getCreatedAt())
                .updatedAt(mf.getUpdatedAt())
                .build();
    }

    private Map<Long, String> resolveAuthorNames(List<MarketplaceFlow> flows) {
        Set<Long> authorIds = flows.stream().map(MarketplaceFlow::getAuthorId).collect(Collectors.toSet());
        if (authorIds.isEmpty()) return Map.of();
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private Map<String, Object> deserializeDsl(String dsl) {
        if (dsl == null || dsl.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(dsl, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize DSL, returning empty map: {}", e.getMessage());
            return Map.of();
        }
    }
}