
package com.github.bhandarisachin.webstore.dto;

public record CustomerRequest(
        String firstName,
        String lastName,
        String email,
        String phone
) {
}
