package com.rpacloud.proxy.service;

import java.util.List;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.proxy.dto.ProxyApiConfigResponse;
import com.rpacloud.proxy.entity.ProxyApiConfig;
import com.rpacloud.proxy.repository.ProxyApiConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProxyApiConfigService {

    private final ProxyApiConfigRepository repository;

    @Transactional(readOnly = true)
    public List<ProxyApiConfigResponse> list(Long userId) {
        return repository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProxyApiConfigResponse create(Long userId, String name, String baseUrl, String paramsJson) {
        ProxyApiConfig config = ProxyApiConfig.builder()
                .userId(userId)
                .name(name)
                .baseUrl(baseUrl)
                .paramsJson(paramsJson)
                .build();
        return toResponse(repository.save(config));
    }

    @Transactional
    public ProxyApiConfigResponse update(Long id, Long userId, String name, String baseUrl, String paramsJson) {
        ProxyApiConfig config = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "API config not found"));
        config.setName(name);
        config.setBaseUrl(baseUrl);
        config.setParamsJson(paramsJson);
        return toResponse(repository.save(config));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        ProxyApiConfig config = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "API config not found"));
        repository.delete(config);
    }

    private ProxyApiConfigResponse toResponse(ProxyApiConfig c) {
        return new ProxyApiConfigResponse(c.getId(), c.getName(), c.getBaseUrl(),
                c.getParamsJson(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
