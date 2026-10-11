
package com.github.bhandarisachin.webstore;

import com.github.bhandarisachin.webstore.dto.OrderRequest;
import com.github.bhandarisachin.webstore.repository.CustomerRepository;
import com.github.bhandarisachin.webstore.repository.OrderRepository;
import com.github.bhandarisachin.webstore.repository.ProductRepository;
import com.github.bhandarisachin.webstore.service.OrderService;
import com.github.bhandarisachin.webstore.dto.OrderItemRequest;
import com.github.bhandarisachin.webstore.entity.Customer;
import com.github.bhandarisachin.webstore.entity.Product;
import com.github.bhandarisachin.webstore.dto.OrderResponse;
import com.github.bhandarisachin.webstore.entity.Order;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServiceTest {

    private OrderService orderService;

    private OrderRepository orderRepository;
    private CustomerRepository customerRepository;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(ProductRepository.class);

        orderService = new OrderService(
                orderRepository,
                customerRepository,
                productRepository
        );
    }

    @Test
    void shouldRejectOrderWithNoItems() {

        OrderRequest request = new OrderRequest(
                1,
                null,
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        verifyNoInteractions(
                orderRepository,
                customerRepository,
                productRepository
        );
    }


    @Test
    void shouldRejectOrderWhenStockIsInsufficient() {

        // Arrange: customer exists
        Customer customer = mock(Customer.class);

        when(customerRepository.findById(1))
                .thenReturn(Optional.of(customer));

        // Arrange: product has only 5 units available
        Product product = mock(Product.class);

        when(product.getId()).thenReturn(10);
        when(product.getStockQuantity()).thenReturn(5);

        when(productRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(product));

        // Request 10 units, but only 5 are available
        OrderRequest request = new OrderRequest(
                1,
                null,
                List.of(new OrderItemRequest(10, 10))
        );

        // Act: order creation must fail
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        // Assert: verify the error
        assertEquals(
                "Insufficient stock for product: 10",
                exception.getMessage()
        );

        // Assert: inventory and orders were not changed
        verify(product, never()).setStockQuantity(anyInt());
        verify(orderRepository, never()).saveAndFlush(any());
    }


    @Test
    void shouldRejectDuplicateProducts() {

        OrderRequest request = new OrderRequest(
                1,
                null,
                List.of(
                        new OrderItemRequest(10, 2),
                        new OrderItemRequest(10, 3)
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        assertEquals(
                "Duplicate product ID: 10",
                exception.getMessage()
        );

        verifyNoInteractions(
                orderRepository,
                customerRepository,
                productRepository
        );
    }


    @Test
    void shouldCreateOrderAndReduceStock() {

        // Arrange: existing customer
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(1);

        when(customerRepository.findById(1))
                .thenReturn(Optional.of(customer));

        // Arrange: product with 20 units in stock
        Product product = mock(Product.class);

        when(product.getId()).thenReturn(10);
        when(product.getStockQuantity()).thenReturn(20);
        when(product.getPrice()).thenReturn(new BigDecimal("49.99"));
        when(product.getName()).thenReturn("Keyboard");

        when(productRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(product));

        // Simulate JPA assigning an order ID
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);

                    // Return the order as if saved.
                    // The ID is not assigned by this mock.
                    return order;
                });

        OrderRequest request = new OrderRequest(
                1,
                null,
                List.of(new OrderItemRequest(10, 2))
        );

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert: stock reduced from 20 to 18
        verify(product).setStockQuantity(18);

        // Assert: order was saved
        ArgumentCaptor<Order> captor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).saveAndFlush(captor.capture());

        Order savedOrder = captor.getValue();

        assertEquals("NEW", savedOrder.getStatus());
        assertEquals(1, savedOrder.getItems().size());

        // Assert: price and total
        assertEquals(
                0,
                new BigDecimal("49.99")
                        .compareTo(savedOrder.getItems().get(0).getUnitPrice())
        );

        assertEquals(
                0,
                new BigDecimal("99.98").compareTo(response.totalAmount())
        );

        assertEquals(2, response.items().get(0).quantity());
    }



}
