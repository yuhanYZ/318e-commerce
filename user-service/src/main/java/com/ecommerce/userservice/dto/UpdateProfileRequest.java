package com.ecommerce.userservice.dto;

/** Partial update — every field optional; a null field is left unchanged. */
public record UpdateProfileRequest(String name, String address, String phone) {
}
