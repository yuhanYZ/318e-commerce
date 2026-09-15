package com.ecommerce.productservice.domain;

/**
 * {@code ProductStatus} value object - domain-models §2.2.4.
 *
 * <p>{@link #INACTIVE} is terminal: reactivation is out of scope for iteration 2.
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE
}
