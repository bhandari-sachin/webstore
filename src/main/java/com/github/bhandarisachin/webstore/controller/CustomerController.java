
package com.github.bhandarisachin.webstore.controller;

import com.github.bhandarisachin.webstore.dto.CustomerRequest;
import com.github.bhandarisachin.webstore.dto.CustomerResponse;
import com.github.bhandarisachin.webstore.service.CustomerService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerResponse> getAllCustomers() {
        return service.getAllCustomers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable Integer id) {

        return service.getCustomerById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestBody CustomerRequest request) {

        CustomerResponse created = service.createCustomer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(created);
    }
}
