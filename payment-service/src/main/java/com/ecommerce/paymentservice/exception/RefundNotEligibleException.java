package com.ecommerce.paymentservice.exception;

/**
 * Thrown for PAY3 when a refund request doesn't meet eligibility: the
 * payment wasn't SUCCESS, the associated order isn't CANCELLED (see
 * PaymentService's javadoc on the "RETURNED" status that specs/2-user
 * -stories.md mentions but specs/3-domain-models.md's OrderStatus doesn't
 * define), or the requested refund amount exceeds what was actually paid.
 */
public class RefundNotEligibleException extends RuntimeException {
    public RefundNotEligibleException(String message) {
        super(message);
    }
}
