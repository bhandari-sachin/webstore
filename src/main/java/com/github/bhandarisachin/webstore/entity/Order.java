
package com.github.bhandarisachin.webstore.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // MariaDB automatically generates the order date
    @Generated
    @Column(
            name = "order_date",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime orderDate;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;

    @Column(name = "status", length = 50)
    private String status = "NEW";

    // One order can contain multiple order items
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.PERSIST,
            fetch = FetchType.LAZY
    )
    private List<OrderItem> items = new ArrayList<>();

    // Required by JPA
    public Order() {
    }

    // Getters and setters

    public Integer getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public LocalDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDateTime deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Integer getShippingAddressId() {
        return shippingAddressId;
    }

    public void setShippingAddressId(Integer shippingAddressId) {
        this.shippingAddressId = shippingAddressId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    // Maintain both sides of the bidirectional relationship
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
