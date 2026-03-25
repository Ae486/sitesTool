package com.rpacloud.proxy.repository;

import com.rpacloud.proxy.entity.ProxyHealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProxyHealthLogRepository extends JpaRepository<ProxyHealthLog, Long> {

    @Modifying
    @Query("DELETE FROM ProxyHealthLog h WHERE h.proxy.id = :proxyId")
    void deleteAllByProxyId(Long proxyId);

    Page<ProxyHealthLog> findByProxyIdOrderByCheckedAtDesc(Long proxyId, Pageable pageable);
}
