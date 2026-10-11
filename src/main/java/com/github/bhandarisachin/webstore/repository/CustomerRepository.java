
package com.github.bhandarisachin.webstore.repository;

import com.github.bhandarisachin.webstore.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository
        extends JpaRepository<Customer, Integer> {
}
