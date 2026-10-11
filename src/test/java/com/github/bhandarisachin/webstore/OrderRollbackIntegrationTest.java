
package com.github.bhandarisachin.webstore;

import com.github.bhandarisachin.webstore.dto.OrderItemRequest;
import com.github.bhandarisachin.webstore.dto.OrderRequest;
import com.github.bhandarisachin.webstore.entity.Customer;
import com.github.bhandarisachin.webstore.entity.Product;
import com.github.bhandarisachin.webstore.repository.CustomerRepository;
import com.github.bhandarisachin.webstore.repository.OrderRepository;
import com.github.bhandarisachin.webstore.repository.ProductRepository;
import com.github.bhandarisachin.webstore.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderRollbackIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldRollbackInventoryWhenOrderFails() {

        Customer customer = new Customer();
        customer.setFirstName("Rollback");
        customer.setLastName("Test");
        customer.setEmail("rollback-integration@example.com");
        customer = customerRepository.saveAndFlush(customer);

        Product first = new Product();
        first.setName("Rollback Keyboard");
        first.setPrice(new BigDecimal("50.00"));
        first.setStockQuantity(20);
        first = productRepository.saveAndFlush(first);

        Product second = new Product();
        second.setName("Rollback Mouse");
        second.setPrice(new BigDecimal("25.00"));
        second.setStockQuantity(1);
        second = productRepository.saveAndFlush(second);

        Integer firstId = first.getId();
        Integer secondId = second.getId();

        assertTrue(firstId < secondId);

        long ordersBefore = orderRepository.count();

        try {
            OrderRequest request = new OrderRequest(
                    customer.getId(),
                    null,
                    List.of(
                            new OrderItemRequest(firstId, 2),
                            new OrderItemRequest(secondId, 10)
                    )
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> orderService.createOrder(request)
            );

            Product firstAfter = productRepository.findById(firstId)
                    .orElseThrow();

            Product secondAfter = productRepository.findById(secondId)
                    .orElseThrow();

            assertEquals(20, firstAfter.getStockQuantity());
            assertEquals(1, secondAfter.getStockQuantity());
            assertEquals(ordersBefore, orderRepository.count());

        } finally {
            productRepository.deleteById(firstId);
            productRepository.deleteById(secondId);
            customerRepository.deleteById(customer.getId());
        }
    }
}
