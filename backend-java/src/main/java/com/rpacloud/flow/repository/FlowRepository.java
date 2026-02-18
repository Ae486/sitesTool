package com.rpacloud.flow.repository;

import com.rpacloud.flow.entity.AutomationFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowRepository extends JpaRepository<AutomationFlow, Long> {
    Page<AutomationFlow> findAll(Pageable pageable);
}
