package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository
        extends JpaRepository<Product, Integer> {
}