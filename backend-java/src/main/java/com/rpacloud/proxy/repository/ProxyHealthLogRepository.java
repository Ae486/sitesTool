package com.rpacloud.proxy.repository;

import com.rpacloud.proxy.entity.ProxyHealthLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProxyHealthLogRepository extends JpaRepository<ProxyHealthLog, Long> {
}
