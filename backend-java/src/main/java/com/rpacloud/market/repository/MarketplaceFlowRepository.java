package com.rpacloud.market.repository;

import java.util.List;
import java.util.Optional;

import com.rpacloud.market.entity.MarketplaceFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceFlowRepository extends JpaRepository<MarketplaceFlow, Long> {

    Page<MarketplaceFlow> findByIsActiveTrueAndVisibility(String visibility, Pageable pageable);

    Optional<MarketplaceFlow> findByIdAndIsActiveTrue(Long id);

    List<MarketplaceFlow> findByAuthorIdAndIsActiveTrue(Long authorId);

    Optional<MarketplaceFlow> findByFlowIdAndAuthorIdAndIsActiveTrue(Long flowId, Long authorId);

    @Modifying
    @Query("UPDATE MarketplaceFlow m SET m.downloadCount = m.downloadCount + 1 WHERE m.id = :id")
    void incrementDownloadCount(Long id);
}
