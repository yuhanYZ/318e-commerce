package com.ecommerce.userservice.domain;

/**
 * Account role. CUSTOMER is the default role assigned on registration (U1);
 * ADMIN is reserved for administrative operations in other services (e.g. P4, I3, I4, PAY4).
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
