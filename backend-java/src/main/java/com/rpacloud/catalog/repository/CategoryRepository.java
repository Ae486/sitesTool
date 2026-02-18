package com.rpacloud.catalog.repository;

import java.util.List;
import java.util.Optional;

import com.rpacloud.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByNameAsc();
}
