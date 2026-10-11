
package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPriceHistoryRepository
        extends JpaRepository<ProductPriceHistory, Integer> {

    List<ProductPriceHistory>
    findByProductIdOrderByChangedAtDescIdDesc(Integer productId);
}
