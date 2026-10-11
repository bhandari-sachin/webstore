package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.OrderItem;
import com.github.bhandarisachin.webstore.entity.OrderItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, OrderItemId> {
}