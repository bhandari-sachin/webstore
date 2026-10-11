
package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.ProductCatalogView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCatalogViewRepository
        extends JpaRepository<ProductCatalogView, Integer> {
}
