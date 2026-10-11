
package com.github.bhandarisachin.webstore.dto;

public record CustomerResponse(
        Integer id,
        String firstName,
        String lastName,
        String email,
        String phone
) {
}
