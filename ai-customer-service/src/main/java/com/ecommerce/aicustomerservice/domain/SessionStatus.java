package com.ecommerce.aicustomerservice.domain;

/**
 * specs/iter2/3-domain-models-ai-customer-service-ddd.md §2.3.1 invariant 1: transitions are
 * restricted to ACTIVE -> ESCALATED and {ACTIVE, ESCALATED} -> CLOSED. No endpoint in this
 * milestone ever produces CLOSED (reserved for future use per the user stories doc).
 */
public enum SessionStatus {
    ACTIVE, ESCALATED, CLOSED
}
