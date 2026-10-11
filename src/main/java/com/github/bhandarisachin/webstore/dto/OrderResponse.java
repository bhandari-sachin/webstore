
package com.github.bhandarisachin.webstore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Integer id,
        Integer customerId,
        LocalDateTime orderDate,
        LocalDateTime deliveryDate,
        Integer shippingAddressId,
        String status,
        List<OrderItemResponse> items,
        BigDecimal totalAmount
) {
}
