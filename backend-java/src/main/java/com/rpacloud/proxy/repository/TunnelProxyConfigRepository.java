package com.rpacloud.proxy.repository;

import java.util.List;
import java.util.Optional;

import com.rpacloud.proxy.entity.TunnelProxyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TunnelProxyConfigRepository extends JpaRepository<TunnelProxyConfig, Long> {
    List<TunnelProxyConfig> findAllByUserId(Long userId);
    Optional<TunnelProxyConfig> findByIdAndUserId(Long id, Long userId);
}
