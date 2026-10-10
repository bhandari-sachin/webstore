package com.github.bhandarisachin.webstore.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Integer id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        Integer categoryId,
        String categoryName,
        Integer supplierId
) {
}