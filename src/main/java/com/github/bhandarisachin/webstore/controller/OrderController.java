
package com.github.bhandarisachin.webstore.controller;

import com.github.bhandarisachin.webstore.dto.OrderRequest;
import com.github.bhandarisachin.webstore.dto.OrderResponse;
import com.github.bhandarisachin.webstore.service.OrderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    // GET all orders
    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return service.getAllOrders();
    }

    // GET an order by ID
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Integer id) {

        return service.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // POST create a new order
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequest request) {

        OrderResponse response = service.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
