package com.rpacloud.proxy.service;

import java.util.List;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.proxy.dto.TunnelProxyConfigResponse;
import com.rpacloud.proxy.entity.TunnelProxyConfig;
import com.rpacloud.proxy.repository.TunnelProxyConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TunnelProxyConfigService {

    private final TunnelProxyConfigRepository repository;

    @Transactional(readOnly = true)
    public List<TunnelProxyConfigResponse> list(Long userId) {
        return repository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TunnelProxyConfigResponse create(Long userId, String name, String protocol,
                                            String host, int port, String username, String password) {
        TunnelProxyConfig config = TunnelProxyConfig.builder()
                .userId(userId)
                .name(name)
                .protocol(normalizeProtocol(protocol))
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .build();
        return toResponse(repository.save(config));
    }

    @Transactional
    public TunnelProxyConfigResponse update(Long id, Long userId, String name, String protocol,
                                            String host, int port, String username, String password) {
        TunnelProxyConfig config = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Tunnel config not found"));
        config.setName(name);
        config.setProtocol(normalizeProtocol(protocol));
        config.setHost(host);
        config.setPort(port);
        config.setUsername(username);
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        return toResponse(repository.save(config));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        TunnelProxyConfig config = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Tunnel config not found"));
        repository.delete(config);
    }

    private TunnelProxyConfigResponse toResponse(TunnelProxyConfig c) {
        return new TunnelProxyConfigResponse(c.getId(), c.getName(), c.getProtocol(),
                c.getHost(), c.getPort(), c.getUsername(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private String normalizeProtocol(String protocol) {
        if (protocol == null) return "http";
        return switch (protocol.toLowerCase().trim()) {
            case "socks5" -> "socks5";
            default -> "http";
        };
    }
}
