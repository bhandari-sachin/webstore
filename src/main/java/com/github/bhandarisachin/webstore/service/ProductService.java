package com.github.bhandarisachin.webstore.service;

import com.github.bhandarisachin.webstore.dto.ProductResponse;
import com.github.bhandarisachin.webstore.entity.Product;
import com.github.bhandarisachin.webstore.entity.ProductCategory;
import com.github.bhandarisachin.webstore.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.bhandarisachin.webstore.dto.ProductRequest;
import com.github.bhandarisachin.webstore.repository.ProductCategoryRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {


    private final ProductRepository repository;
    private final ProductCategoryRepository categoryRepository;

    public ProductService(
            ProductRepository repository,
            ProductCategoryRepository categoryRepository) {

        this.repository = repository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> getProductById(Integer id) {
        return repository.findById(id)
                .map(this::toResponse);
    }

    private ProductResponse toResponse(Product product) {

        ProductCategory category = product.getCategory();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                product.getSupplierId()
        );
    }


    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        Product product = new Product();

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setSupplierId(request.supplierId());

        if (request.categoryId() != null) {
            ProductCategory category = categoryRepository
                    .findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found: " + request.categoryId()
                    ));

            product.setCategory(category);
        }

        Product saved = repository.save(product);

        return toResponse(saved);
    }


    @Transactional
    public Optional<ProductResponse> updateProduct(
            Integer id, ProductRequest request) {

        return repository.findById(id).map(product -> {

            product.setName(request.name());
            product.setDescription(request.description());
            product.setPrice(request.price());
            product.setStockQuantity(request.stockQuantity());

            if (request.categoryId() != null) {
                ProductCategory category = categoryRepository
                        .findById(request.categoryId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Category not found: " + request.categoryId()
                        ));

                product.setCategory(category);
            } else {
                product.setCategory(null);
            }

            product.setSupplierId(request.supplierId());

            Product updated = repository.save(product);

            return toResponse(updated);
        });
    }

    @Transactional
    public boolean deleteProduct(Integer id) {

        if (!repository.existsById(id)) {
            return false;
        }

        repository.deleteById(id);
        return true;
    }
}