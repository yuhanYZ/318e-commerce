package com.ecommerce.userservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** All fields are optional; only non-null fields are applied (U3). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    @Size(max = 500, message = "address must be at most 500 characters")
    private String address;

    @Size(max = 30, message = "phone must be at most 30 characters")
    private String phone;
}
