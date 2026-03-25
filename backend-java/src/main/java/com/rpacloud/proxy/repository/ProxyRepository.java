package com.rpacloud.proxy.repository;

import java.util.List;
import java.util.Optional;

import com.rpacloud.proxy.entity.Proxy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProxyRepository extends JpaRepository<Proxy, Long>, JpaSpecificationExecutor<Proxy> {

    List<Proxy> findAllByIsActiveTrue();

    long countByIsActiveTrue();

    Optional<Proxy> findByIpAndPort(String ip, Integer port);

    Optional<Proxy> findFirstByIsActiveTrueOrderBySuccessCountDesc();
}
