package com.github.bhandarisachin.webstore.controller;

import com.github.bhandarisachin.webstore.dto.ProductResponse;
import com.github.bhandarisachin.webstore.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.github.bhandarisachin.webstore.dto.ProductRequest;
import org.springframework.http.HttpStatus;
import com.github.bhandarisachin.webstore.entity.ProductPriceHistory;
import com.github.bhandarisachin.webstore.service.ProductPriceHistoryService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductPriceHistoryService priceHistoryService;

    private final ProductService service;

    public ProductController(
            ProductService productService,
            ProductPriceHistoryService priceHistoryService) {

        this.service = productService;
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return service.getAllProducts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Integer id) {

        return service.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody ProductRequest request) {

        ProductResponse created = service.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Integer id,
            @RequestBody ProductRequest request) {

        return service.updateProduct(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Integer id) {

        boolean deleted = service.deleteProduct(id);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{id}/price-history")
    public List<ProductPriceHistory> getProductPriceHistory(
            @PathVariable Integer id) {

        return priceHistoryService.getPriceHistory(id);
    }

}