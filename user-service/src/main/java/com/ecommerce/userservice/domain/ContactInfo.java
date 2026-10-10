package com.ecommerce.userservice.domain;

import jakarta.persistence.Embeddable;

/**
 * Value Object — specs/iter2/3-domain-models-user-service-ddd.md §2.2.4. Encapsulates the
 * address and phone number together, so a profile update replaces one immutable value rather
 * than mutating two loose strings.
 */
@Embeddable
public record ContactInfo(String address, String phone) {

    public static ContactInfo empty() {
        return new ContactInfo(null, null);
    }

    public ContactInfo withAddress(String newAddress) {
        return new ContactInfo(newAddress, phone);
    }

    public ContactInfo withPhone(String newPhone) {
        return new ContactInfo(address, newPhone);
    }
}
