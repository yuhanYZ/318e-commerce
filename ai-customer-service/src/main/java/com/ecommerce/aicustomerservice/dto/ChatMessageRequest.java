package com.ecommerce.aicustomerservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank String content
) {
}
