package com.rpacloud.site.repository;

import java.util.List;
import java.util.Optional;

import com.rpacloud.site.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteRepository extends JpaRepository<Site, Long> {

    @Query("SELECT s.id FROM Site s")
    Page<Long> findAllIds(Pageable pageable);

    @Query("SELECT DISTINCT s FROM Site s LEFT JOIN FETCH s.tags LEFT JOIN FETCH s.category WHERE s.id IN :ids ORDER BY s.id")
    List<Site> findAllWithTagsByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT s FROM Site s LEFT JOIN FETCH s.tags LEFT JOIN FETCH s.category WHERE s.id = :id")
    Optional<Site> findByIdWithTags(@Param("id") Long id);
}
