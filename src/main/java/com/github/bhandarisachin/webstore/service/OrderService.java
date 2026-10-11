
package com.github.bhandarisachin.webstore.service;

import com.github.bhandarisachin.webstore.dto.OrderRequest;
import com.github.bhandarisachin.webstore.dto.OrderItemRequest;
import com.github.bhandarisachin.webstore.dto.OrderItemResponse;
import com.github.bhandarisachin.webstore.dto.OrderResponse;

import com.github.bhandarisachin.webstore.entity.Customer;
import com.github.bhandarisachin.webstore.entity.Order;
import com.github.bhandarisachin.webstore.entity.OrderItem;
import com.github.bhandarisachin.webstore.entity.Product;

import com.github.bhandarisachin.webstore.repository.CustomerRepository;
import com.github.bhandarisachin.webstore.repository.OrderRepository;
import com.github.bhandarisachin.webstore.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderService(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository) {

        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    // CREATE ORDER
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        // 1. Validate request
        if (request == null ||
                request.customerId() == null ||
                request.items() == null ||
                request.items().isEmpty()) {

            throw new IllegalArgumentException(
                    "Customer and at least one order item are required");
        }

        // 2. Validate products, quantities and duplicates
        Set<Integer> productIds = new HashSet<>();

        for (OrderItemRequest item : request.items()) {

            if (item == null ||
                    item.productId() == null ||
                    item.quantity() <= 0) {

                throw new IllegalArgumentException(
                        "Each item needs a product ID and positive quantity");
            }

            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException(
                        "Duplicate product ID: " + item.productId());
            }
        }

        // 3. Verify customer
        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: " + request.customerId()));

        // Shipping address support will be implemented later
        if (request.shippingAddressId() != null) {
            throw new IllegalArgumentException(
                    "Shipping addresses are not supported yet");
        }

        // 4. Create order
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus("NEW");

        // 5. Sort product IDs to reduce deadlock risks
        List<OrderItemRequest> sortedItems = request.items()
                .stream()
                .sorted(Comparator.comparing(
                        OrderItemRequest::productId))
                .toList();

        // 6. Lock products and validate available stock
        for (OrderItemRequest requestedItem : sortedItems) {

            Product product = productRepository
                    .findByIdForUpdate(requestedItem.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product not found: " + requestedItem.productId()));

            if (product.getStockQuantity() < requestedItem.quantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: " + product.getId());
            }

            // 7. Reduce inventory
            product.setStockQuantity(
                    product.getStockQuantity() - requestedItem.quantity());

            // 8. Create order item using current product price
            OrderItem orderItem = new OrderItem(
                    order,
                    product,
                    requestedItem.quantity(),
                    product.getPrice()
            );

            order.addItem(orderItem);
        }

        // 9. Save order and cascade persistence to order items
        Order savedOrder = orderRepository.saveAndFlush(order);

        // 10. Return order response
        return toResponse(savedOrder);
    }

    // GET ALL ORDERS
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {

        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET ORDER BY ID
    @Transactional(readOnly = true)
    public Optional<OrderResponse> getOrderById(Integer id) {

        return orderRepository.findById(id)
                .map(this::toResponse);
    }

    // CONVERT ORDER ENTITY TO RESPONSE DTO
    private OrderResponse toResponse(Order order) {

        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal total = items.stream()
                .map(OrderItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getOrderDate(),
                order.getDeliveryDate(),
                order.getShippingAddressId(),
                order.getStatus(),
                items,
                total
        );
    }

    // CONVERT ORDER ITEM TO RESPONSE DTO
    private OrderItemResponse toItemResponse(OrderItem item) {

        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal
        );
    }
}
