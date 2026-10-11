
package com.github.bhandarisachin.webstore.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Integer productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
