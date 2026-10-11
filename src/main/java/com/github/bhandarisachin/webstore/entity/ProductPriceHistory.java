
package com.github.bhandarisachin.webstore.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "product_price_history")
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "old_price", nullable = false)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false)
    private BigDecimal newPrice;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected ProductPriceHistory() {
    }

    public Integer getId() {
        return id;
    }

    public Integer getProductId() {
        return productId;
    }

    public BigDecimal getOldPrice() {
        return oldPrice;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
