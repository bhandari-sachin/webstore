package com.github.bhandarisachin.webstore.dto;

import java.math.BigDecimal;

public record ProductRequest(
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        Integer categoryId,
        Integer supplierId
) {
}