package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository
        extends JpaRepository<Order, Integer> {
}