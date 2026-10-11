
package com.github.bhandarisachin.webstore.service;

import com.github.bhandarisachin.webstore.entity.ProductPriceHistory;
import com.github.bhandarisachin.webstore.repository.ProductPriceHistoryRepository;
import com.github.bhandarisachin.webstore.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductPriceHistoryService {

    private final ProductPriceHistoryRepository historyRepository;
    private final ProductRepository productRepository;

    public ProductPriceHistoryService(
            ProductPriceHistoryRepository historyRepository,
            ProductRepository productRepository) {

        this.historyRepository = historyRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductPriceHistory> getPriceHistory(Integer productId) {

        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException(
                    "Product not found: " + productId);
        }

        return historyRepository
                .findByProductIdOrderByChangedAtDescIdDesc(productId);
    }
}
