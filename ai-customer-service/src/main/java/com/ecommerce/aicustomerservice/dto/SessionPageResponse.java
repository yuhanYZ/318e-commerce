package com.ecommerce.aicustomerservice.dto;

import java.util.List;

public record SessionPageResponse(
        List<SessionSummaryResponse> content,
        int page,
        long totalElements
) {
}
