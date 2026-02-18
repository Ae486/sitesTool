package com.rpacloud.history.repository;

import com.rpacloud.history.entity.CheckinHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoryRepository extends JpaRepository<CheckinHistory, Long> {

    Page<CheckinHistory> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<CheckinHistory> findByFlowIdOrderByStartedAtDesc(Long flowId, Pageable pageable);

    @Query("SELECT h FROM CheckinHistory h WHERE h.errorTypes LIKE %:pattern% ORDER BY h.startedAt DESC")
    Page<CheckinHistory> findByErrorTypeContaining(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT h FROM CheckinHistory h WHERE h.flowId = :flowId AND h.errorTypes LIKE %:pattern% ORDER BY h.startedAt DESC")
    Page<CheckinHistory> findByFlowIdAndErrorTypeContaining(@Param("flowId") Long flowId, @Param("pattern") String pattern, Pageable pageable);

    long countByFlowId(Long flowId);
}
