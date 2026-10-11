
package com.github.bhandarisachin.webstore.service;

import com.github.bhandarisachin.webstore.dto.CustomerRequest;
import com.github.bhandarisachin.webstore.dto.CustomerResponse;
import com.github.bhandarisachin.webstore.entity.Customer;
import com.github.bhandarisachin.webstore.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CustomerResponse> getCustomerById(Integer id) {
        return repository.findById(id)
                .map(this::toResponse);
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        Customer customer = new Customer();

        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());

        return toResponse(repository.save(customer));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhone()
        );
    }
}
