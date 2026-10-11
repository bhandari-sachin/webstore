
package com.github.bhandarisachin.webstore.controller;

import com.github.bhandarisachin.webstore.entity.ProductCatalogView;
import com.github.bhandarisachin.webstore.service.ProductCatalogViewService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/catalog")
public class ProductCatalogViewController {

    private final ProductCatalogViewService service;

    public ProductCatalogViewController(
            ProductCatalogViewService service) {
        this.service = service;
    }

    // GET all products from the database view
    @GetMapping
    public List<ProductCatalogView> getAllProducts() {
        return service.getAllProducts();
    }

    // GET one product from the database view
    @GetMapping("/{id}")
    public ResponseEntity<ProductCatalogView> getProductById(
            @PathVariable Integer id) {

        return service.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
