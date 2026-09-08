package com.ecommerce.productservice.domain;

/**
 * Lifecycle status of a {@link Product}.
 * {@code INACTIVE} is set by an Admin deactivating a product (P4); deactivated
 * products no longer appear in P1 (browse) or P3 (search) results.
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE
}
