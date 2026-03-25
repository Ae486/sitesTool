package com.rpacloud.proxy.repository;

import java.util.List;
import java.util.Optional;

import com.rpacloud.proxy.entity.ProxyApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProxyApiConfigRepository extends JpaRepository<ProxyApiConfig, Long> {
    List<ProxyApiConfig> findAllByUserId(Long userId);
    Optional<ProxyApiConfig> findByIdAndUserId(Long id, Long userId);
}
