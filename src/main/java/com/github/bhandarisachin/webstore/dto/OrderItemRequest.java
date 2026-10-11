package com.github.bhandarisachin.webstore.dto;

public record OrderItemRequest(
        Integer productId,
        int quantity
) {
}