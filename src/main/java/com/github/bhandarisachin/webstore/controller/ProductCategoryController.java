package com.github.bhandarisachin.webstore.controller;

import com.github.bhandarisachin.webstore.entity.ProductCategory;
import com.github.bhandarisachin.webstore.service.ProductCategoryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class ProductCategoryController {

    private final ProductCategoryService service;

    public ProductCategoryController(
            ProductCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductCategory> getAllCategories() {
        return service.getAllCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductCategory> getCategoryById(
            @PathVariable Integer id) {

        return service.getCategoryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductCategory> createCategory(
            @RequestBody ProductCategory category) {

        category.setId(null);

        ProductCategory saved = service.createCategory(category);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductCategory> updateCategory(
            @PathVariable Integer id,
            @RequestBody ProductCategory category) {

        return service.updateCategory(id, category)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}