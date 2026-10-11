
package com.github.bhandarisachin.webstore;

import com.github.bhandarisachin.webstore.dto.OrderRequest;
import com.github.bhandarisachin.webstore.dto.OrderItemRequest;
import com.github.bhandarisachin.webstore.dto.OrderResponse;

import com.github.bhandarisachin.webstore.entity.Customer;
import com.github.bhandarisachin.webstore.entity.Product;
import com.github.bhandarisachin.webstore.entity.ProductCategory;

import com.github.bhandarisachin.webstore.repository.CustomerRepository;
import com.github.bhandarisachin.webstore.repository.ProductRepository;
import com.github.bhandarisachin.webstore.repository.ProductCategoryRepository;
import com.github.bhandarisachin.webstore.repository.OrderRepository;

import com.github.bhandarisachin.webstore.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldSaveCustomerInTestDatabase() {

        Customer customer = new Customer();
        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setEmail("integration-test@example.com");

        Customer saved = customerRepository.saveAndFlush(customer);

        assertNotNull(saved.getId());

        assertTrue(
                customerRepository.existsById(saved.getId())
        );
    }


    @Test
    void shouldCreateOrderAndReduceStock() {

        // 1. Create a test customer
        Customer customer = new Customer();
        customer.setFirstName("Order");
        customer.setLastName("Tester");
        customer.setEmail("order-test@example.com");

        customer = customerRepository.saveAndFlush(customer);

        // 2. Create a test category
        ProductCategory category = new ProductCategory();
        category.setName("Test Electronics");
        category.setDescription("Integration test category");

        category = categoryRepository.saveAndFlush(category);

        // 3. Create a test product
        Product product = new Product();
        product.setName("Test Keyboard");
        product.setDescription("Integration test product");
        product.setPrice(new BigDecimal("49.99"));
        product.setStockQuantity(20);
        product.setCategory(category);

        product = productRepository.saveAndFlush(product);

        // 4. Create an order for 2 keyboards
        OrderRequest request = new OrderRequest(
                customer.getId(),
                null,
                List.of(new OrderItemRequest(product.getId(), 2))
        );

        OrderResponse response = orderService.createOrder(request);

        // 5. Verify the order
        assertNotNull(response.id());
        assertEquals("NEW", response.status());
        assertEquals(1, response.items().size());

        // 6. Verify the total and historical unit price
        assertEquals(
                0,
                new BigDecimal("99.98").compareTo(response.totalAmount())
        );

        assertEquals(
                0,
                new BigDecimal("49.99")
                        .compareTo(response.items().get(0).unitPrice())
        );

        // 7. Verify order persistence
        assertTrue(orderRepository.existsById(response.id()));

        // 8. Verify inventory reduction
        productRepository.flush();

        assertEquals(
                18,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }


    @Test
    void shouldRollbackWhenSecondProductHasInsufficientStock() {

        // Create customer
        Customer customer = new Customer();
        customer.setFirstName("Rollback");
        customer.setLastName("Tester");
        customer.setEmail("rollback-test@example.com");
        customer = customerRepository.saveAndFlush(customer);

        // Product 1: sufficient stock
        Product product1 = new Product();
        product1.setName("Test Keyboard");
        product1.setPrice(new BigDecimal("50.00"));
        product1.setStockQuantity(20);
        product1 = productRepository.saveAndFlush(product1);

        // Product 2: insufficient stock
        Product product2 = new Product();
        product2.setName("Test Mouse");
        product2.setPrice(new BigDecimal("25.00"));
        product2.setStockQuantity(1);
        product2 = productRepository.saveAndFlush(product2);

        // Remember product IDs
        Integer product1Id = product1.getId();
        Integer product2Id = product2.getId();

        // OrderService processes product IDs in ascending order
        assertTrue(product1Id < product2Id);

        OrderRequest request = new OrderRequest(
                customer.getId(),
                null,
                List.of(
                        new OrderItemRequest(product1Id, 2),
                        new OrderItemRequest(product2Id, 10)
                )
        );

        // The second product should cause the order to fail
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        // Verify that the transaction has been marked for rollback
        assertTrue(TestTransaction.isActive());

        assertTrue(TestTransaction.isFlaggedForRollback());
    }


}
