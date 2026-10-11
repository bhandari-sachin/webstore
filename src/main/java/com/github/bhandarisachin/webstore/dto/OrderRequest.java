package com.github.bhandarisachin.webstore.dto;

import java.util.List;

public record OrderRequest(
        Integer customerId,
        Integer shippingAddressId,
        List<OrderItemRequest> items
) {
}