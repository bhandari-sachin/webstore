package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository
        extends JpaRepository<ProductCategory, Integer> {
}