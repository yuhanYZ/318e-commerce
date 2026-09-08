package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.domain.RefundStatus;
import com.ecommerce.paymentservice.dto.external.OrderInfo;
import com.ecommerce.paymentservice.dto.external.OrderItemInfo;
import com.ecommerce.paymentservice.dto.request.PaymentRequest;
import com.ecommerce.paymentservice.dto.request.RefundRequest;
import com.ecommerce.paymentservice.dto.response.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.response.RefundResponse;
import com.ecommerce.paymentservice.exception.OrderAccessDeniedException;
import com.ecommerce.paymentservice.exception.OrderNotPayableException;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.RefundNotEligibleException;
import com.ecommerce.paymentservice.messaging.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests (no Spring context, no Kafka broker, no Order Service) for
 * PaymentService's branching logic - the payability/ownership/amount checks
 * in PAY1 and the eligibility checks in PAY3 are exercised here directly,
 * since they're easier to isolate at this layer than through MockMvc.
 * MockPaymentGateway is used as a real (deterministic) collaborator rather
 * than mocked, matching PaymentControllerIntegrationTest's approach.
 */
class PaymentServiceTest {

    private static final String DECLINE_TRIGGER = "MOCK_CARD_DECLINE";

    private final PaymentRepository paymentRepository = org.mockito.Mockito.mock(PaymentRepository.class);
    private final RefundRepository refundRepository = org.mockito.Mockito.mock(RefundRepository.class);
    private final OrderClient orderClient = org.mockito.Mockito.mock(OrderClient.class);
    private final MockPaymentGateway mockPaymentGateway = new MockPaymentGateway(DECLINE_TRIGGER);
    private final PaymentEventPublisher paymentEventPublisher = org.mockito.Mockito.mock(PaymentEventPublisher.class);

    private final PaymentService paymentService = new PaymentService(
            paymentRepository, refundRepository, orderClient, mockPaymentGateway, paymentEventPublisher);

    @Test
    void payForOrder_success_publishesConfirmedAndReturnsSuccess() {
        OrderInfo order = new OrderInfo("ORD-1", "USR-1", "PENDING", new BigDecimal("39.98"),
                List.of(new OrderItemInfo("PRD-1", 2)));
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = paymentService.payForOrder("USR-1",
                new PaymentRequest("ORD-1", new BigDecimal("39.98"), "VISA_TEST"));

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        verify(paymentEventPublisher).publishPaymentConfirmed(argThat(e -> e.getOrderId().equals("ORD-1")));
        verify(paymentEventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    void payForOrder_declineTrigger_publishesFailedAndReturnsFailed() {
        OrderInfo order = new OrderInfo("ORD-1", "USR-1", "PENDING", new BigDecimal("10.00"), List.of());
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = paymentService.payForOrder("USR-1",
                new PaymentRequest("ORD-1", new BigDecimal("10.00"), DECLINE_TRIGGER));

        assertThat(response.getStatus()).isEqualTo("FAILED");
        verify(paymentEventPublisher).publishPaymentFailed(any());
        verify(paymentEventPublisher, never()).publishPaymentConfirmed(any());
    }

    @Test
    void payForOrder_amountMismatch_publishesFailedAndReturnsFailed() {
        OrderInfo order = new OrderInfo("ORD-1", "USR-1", "PENDING", new BigDecimal("39.98"), List.of());
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = paymentService.payForOrder("USR-1",
                new PaymentRequest("ORD-1", new BigDecimal("10.00"), "VISA_TEST"));

        assertThat(response.getStatus()).isEqualTo("FAILED");
        verify(paymentEventPublisher).publishPaymentFailed(any());
    }

    @Test
    void payForOrder_orderNotFound_throwsOrderNotPayable() {
        when(orderClient.getOrder("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.payForOrder("USR-1",
                new PaymentRequest("missing", BigDecimal.TEN, "VISA_TEST")))
                .isInstanceOf(OrderNotPayableException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void payForOrder_orderNotPending_throwsOrderNotPayable() {
        OrderInfo order = new OrderInfo("ORD-1", "USR-1", "PAID", BigDecimal.TEN, List.of());
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.payForOrder("USR-1",
                new PaymentRequest("ORD-1", BigDecimal.TEN, "VISA_TEST")))
                .isInstanceOf(OrderNotPayableException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void payForOrder_notOwner_throwsAccessDenied() {
        OrderInfo order = new OrderInfo("ORD-1", "SOMEONE-ELSE", "PENDING", BigDecimal.TEN, List.of());
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.payForOrder("USR-1",
                new PaymentRequest("ORD-1", BigDecimal.TEN, "VISA_TEST")))
                .isInstanceOf(OrderAccessDeniedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getPaymentByOrderId_notOwner_throwsAccessDenied() {
        Payment payment = Payment.builder().id("PAY-1").orderId("ORD-1")
                .amount(BigDecimal.TEN).status(PaymentStatus.SUCCESS).method("VISA_TEST").build();
        when(paymentRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(payment));
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(
                new OrderInfo("ORD-1", "SOMEONE-ELSE", "PAID", BigDecimal.TEN, List.of())));

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId("USR-1", "ORD-1"))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void getPaymentByOrderId_noPayment_throwsPaymentNotFound() {
        when(paymentRepository.findByOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId("USR-1", "missing"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void requestRefund_eligible_marksPaymentRefundedAndPublishes() {
        Payment payment = Payment.builder().id("PAY-1").orderId("ORD-1")
                .amount(new BigDecimal("50.00")).status(PaymentStatus.SUCCESS).method("VISA_TEST").build();
        when(paymentRepository.findById("PAY-1")).thenReturn(Optional.of(payment));
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(
                new OrderInfo("ORD-1", "USR-1", "CANCELLED", new BigDecimal("50.00"), List.of())));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse response = paymentService.requestRefund("USR-1", "PAY-1", new RefundRequest(new BigDecimal("20.00")));

        assertThat(response.getStatus()).isEqualTo("PROCESSED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentEventPublisher).publishRefundProcessed(any());
    }

    @Test
    void requestRefund_orderNotCancelled_savesRejectedRefundAndThrows() {
        Payment payment = Payment.builder().id("PAY-1").orderId("ORD-1")
                .amount(new BigDecimal("50.00")).status(PaymentStatus.SUCCESS).method("VISA_TEST").build();
        when(paymentRepository.findById("PAY-1")).thenReturn(Optional.of(payment));
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(
                new OrderInfo("ORD-1", "USR-1", "PAID", new BigDecimal("50.00"), List.of())));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> paymentService.requestRefund("USR-1", "PAY-1", new RefundRequest(new BigDecimal("50.00"))))
                .isInstanceOf(RefundNotEligibleException.class);

        verify(refundRepository).save(argThat(r -> r.getStatus() == RefundStatus.REJECTED));
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishRefundProcessed(any());
    }

    @Test
    void requestRefund_amountExceedsPaid_savesRejectedRefundAndThrows() {
        Payment payment = Payment.builder().id("PAY-1").orderId("ORD-1")
                .amount(new BigDecimal("50.00")).status(PaymentStatus.SUCCESS).method("VISA_TEST").build();
        when(paymentRepository.findById("PAY-1")).thenReturn(Optional.of(payment));
        when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(
                new OrderInfo("ORD-1", "USR-1", "CANCELLED", new BigDecimal("50.00"), List.of())));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> paymentService.requestRefund("USR-1", "PAY-1", new RefundRequest(new BigDecimal("999.00"))))
                .isInstanceOf(RefundNotEligibleException.class);

        verify(refundRepository).save(argThat((Refund r) -> r.getStatus() == RefundStatus.REJECTED));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void listAllPayments_mapsPageCorrectly() {
        Payment payment = Payment.builder().id("PAY-1").orderId("ORD-1")
                .amount(BigDecimal.TEN).status(PaymentStatus.SUCCESS).method("VISA_TEST")
                .createdAt(LocalDateTime.now()).build();
        Page<Payment> page = new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1);
        when(paymentRepository.findAll(any(Pageable.class))).thenReturn(page);

        PaymentPageResponse response = paymentService.listAllPayments(0);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo("PAY-1");
    }
}
