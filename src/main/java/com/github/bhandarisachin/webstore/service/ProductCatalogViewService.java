
package com.github.bhandarisachin.webstore.service;

import com.github.bhandarisachin.webstore.entity.ProductCatalogView;
import com.github.bhandarisachin.webstore.repository.ProductCatalogViewRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductCatalogViewService {

    private final ProductCatalogViewRepository repository;

    public ProductCatalogViewService(
            ProductCatalogViewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductCatalogView> getAllProducts() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ProductCatalogView> getProductById(Integer id) {
        return repository.findById(id);
    }
}
