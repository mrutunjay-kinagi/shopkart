package com.mkinagi.shopkart.payment.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.common.events.DomainEventPublisher;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentFailed;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentRefunded;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentSucceeded;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.Topics;
import com.mkinagi.shopkart.order.service.OrderLookup;
import com.mkinagi.shopkart.order.service.OrderLookup.OrderPaymentView;
import com.mkinagi.shopkart.payment.api.PaymentDtos.MockPayWebhook;
import com.mkinagi.shopkart.payment.api.PaymentDtos.PayRequest;
import com.mkinagi.shopkart.payment.api.PaymentDtos.PaymentResponse;
import com.mkinagi.shopkart.payment.api.PaymentDtos.ReceiptLine;
import com.mkinagi.shopkart.payment.api.PaymentDtos.ReceiptResponse;
import com.mkinagi.shopkart.payment.domain.Payment;
import com.mkinagi.shopkart.payment.domain.PaymentRepository;
import com.mkinagi.shopkart.payment.domain.PaymentStatus;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway.ChargeRequest;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway.ChargeResult;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Charges orders through the {@link PaymentGateway}, records the outcome and announces it on Kafka (via
 * the outbox). The gateway call happens <em>between</em> two short transactions so no database
 * connection or row lock is held while waiting on a third party.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private static final Duration PAY_LOCK_TTL = Duration.ofSeconds(30);

	private final PaymentRepository payments;

	private final OrderLookup orders;

	private final PaymentGateway gateway;

	private final DomainEventPublisher events;

	private final TransactionTemplate tx;

	private final StringRedisTemplate redis;

	private final MeterRegistry meters;

	public PaymentService(PaymentRepository payments, OrderLookup orders, PaymentGateway gateway,
			DomainEventPublisher events, TransactionTemplate tx, StringRedisTemplate redis, MeterRegistry meters) {
		this.payments = payments;
		this.orders = orders;
		this.gateway = gateway;
		this.events = events;
		this.tx = tx;
		this.redis = redis;
		this.meters = meters;
	}

	public PaymentResponse pay(Long userId, Long orderId, PayRequest request) {
		String lockKey = "lock:pay:order:" + orderId;
		// One payment attempt per order at a time, across all instances: stops a double-click from charging twice.
		if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", PAY_LOCK_TTL))) {
			throw ApiException.conflict("PAYMENT_IN_PROGRESS", "A payment for this order is already being processed");
		}
		try {
			Payment payment = tx.execute(status -> startPayment(userId, orderId));
			ChargeResult result = gateway.charge(new ChargeRequest(String.valueOf(payment.getId()), payment.getAmount(),
					payment.getCurrency(), payment.getMethod(), request.cardToken(), request.upiVpa(),
					request.bankCode()));
			return tx.execute(status -> applyChargeResult(payment.getId(), result));
		}
		finally {
			redis.delete(lockKey);
		}
	}

	private Payment startPayment(Long userId, Long orderId) {
		OrderPaymentView order = orders.forPayment(orderId);
		if (!order.userId().equals(userId)) {
			throw ApiException.notFound("Order", orderId);
		}
		if (!order.paymentMethod().isOnline()) {
			throw ApiException.unprocessable("COD_ORDER", "Cash on delivery orders are paid at delivery");
		}
		if (!order.awaitingPayment(Instant.now())) {
			throw ApiException.conflict("ORDER_NOT_PAYABLE",
					"Order " + order.orderNumber() + " is " + order.status() + " and cannot be paid");
		}
		if (payments.existsByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.SUCCEEDED))) {
			throw ApiException.conflict("ALREADY_PAID", "Order " + order.orderNumber() + " is already paid");
		}
		if (payments.existsByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.PENDING))) {
			throw ApiException.conflict("PAYMENT_IN_PROGRESS",
					"Approve or cancel the pending payment request for this order first");
		}
		return payments.saveAndFlush(new Payment(orderId, userId, order.paymentMethod(), order.totalAmount(),
				order.currency(), gateway.name()));
	}

	private PaymentResponse applyChargeResult(Long paymentId, ChargeResult result) {
		Payment payment = payments.findById(paymentId).orElseThrow();
		switch (result.outcome()) {
			case SUCCEEDED -> markSucceeded(payment, result.gatewayReference(), result.instrumentSummary());
			case FAILED -> markFailed(payment, result.gatewayReference(), result.failureReason());
			case PENDING -> payment.awaitConfirmation(result.gatewayReference(), result.instrumentSummary());
		}
		meters.counter("shopkart.payments", "method", payment.getMethod().name(), "outcome", result.outcome().name())
			.increment();
		return PaymentResponse.from(payment);
	}

	/**
	 * Asynchronous confirmation from the gateway. Idempotent: gateways retry webhooks, so a repeat for an
	 * already-settled payment is acknowledged and ignored.
	 */
	@Transactional
	public void handleMockPayWebhook(MockPayWebhook webhook) {
		Payment payment = payments.findByGatewayAndGatewayReference(gateway.name(), webhook.gatewayReference())
			.orElseThrow(() -> ApiException.notFound("Payment", webhook.gatewayReference()));
		if (!payment.isPending()) {
			log.info("Ignoring webhook for settled payment {} ({})", payment.getId(), payment.getStatus());
			return;
		}
		if ("SUCCEEDED".equalsIgnoreCase(webhook.status())) {
			markSucceeded(payment, payment.getGatewayReference(), payment.getInstrumentSummary());
		}
		else if ("FAILED".equalsIgnoreCase(webhook.status())) {
			markFailed(payment, null, webhook.reason() != null ? webhook.reason() : "Declined by customer");
		}
		else {
			throw ApiException.badRequest("UNKNOWN_STATUS", "Unsupported webhook status " + webhook.status());
		}
	}

	/** Cash on delivery: open a payment to be settled when the parcel is delivered. */
	@Transactional
	public void openCashOnDelivery(Long orderId, Long userId, BigDecimal amount) {
		if (payments.findByOrderIdOrderByIdAsc(orderId).isEmpty()) {
			Payment payment = new Payment(orderId, userId, PaymentMethod.COD, amount, "INR", "cod");
			payment.awaitConfirmation(null, "Cash on delivery");
			payments.save(payment);
		}
	}

	@Transactional
	public void settleCashOnDelivery(Long orderId) {
		payments.findByOrderIdOrderByIdAsc(orderId)
			.stream()
			.filter(p -> p.getMethod() == PaymentMethod.COD && p.isPending())
			.forEach(p -> markSucceeded(p, null, "Cash on delivery"));
	}

	/** Order was cancelled: refund captured money and void anything still pending. */
	@Transactional
	public void onOrderCancelled(Long orderId) {
		for (Payment payment : payments.findByOrderIdOrderByIdAsc(orderId)) {
			if (payment.getStatus() == PaymentStatus.SUCCEEDED && payment.getMethod().isOnline()) {
				PaymentGateway.RefundResult refund = gateway.refund(payment.getGatewayReference(), payment.getAmount());
				if (refund.succeeded()) {
					payment.refunded();
					events.publish(Topics.PAYMENT_EVENTS, "Payment", payment.getId(), EventTypes.PAYMENT_REFUNDED,
							new PaymentRefunded(payment.getId(), orderId, payment.getUserId(), payment.getAmount()));
				}
				else {
					throw new IllegalStateException("Refund failed for payment " + payment.getId());
				}
			}
			else if (payment.isPending()) {
				payment.fail(null, "Order cancelled");
			}
		}
	}

	@Transactional(readOnly = true)
	public List<PaymentResponse> forOrder(Long userId, Long orderId) {
		if (!orders.forPayment(orderId).userId().equals(userId)) {
			throw ApiException.notFound("Order", orderId);
		}
		return payments.findByOrderIdOrderByIdAsc(orderId).stream().map(PaymentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public ReceiptResponse receipt(Long userId, Long paymentId) {
		Payment payment = payments.findByIdAndUserId(paymentId, userId)
			.orElseThrow(() -> ApiException.notFound("Payment", paymentId));
		if (payment.getReceiptNumber() == null) {
			throw ApiException.conflict("NO_RECEIPT", "A receipt is issued once the payment succeeds");
		}
		OrderPaymentView order = orders.forPayment(payment.getOrderId());
		List<ReceiptLine> lines = new ArrayList<>(order.lines()
			.stream()
			.map(l -> new ReceiptLine(l.name(), l.quantity(), l.unitPrice(), l.lineTotal()))
			.toList());
		BigDecimal itemsTotal = lines.stream()
			.map(ReceiptLine::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal shipping = payment.getAmount().subtract(itemsTotal);
		if (shipping.signum() > 0) {
			lines.add(new ReceiptLine("Shipping", 1, shipping, shipping));
		}
		return new ReceiptResponse(payment.getReceiptNumber(), order.orderNumber(), payment.getPaidAt(),
				payment.getMethod(), payment.getInstrumentSummary(), payment.getGatewayReference(), lines,
				payment.getAmount(), payment.getCurrency(), "ShopKart Retail Pvt. Ltd.");
	}

	private void markSucceeded(Payment payment, String reference, String instrument) {
		payment.succeed(reference, instrument, Instant.now());
		events.publish(Topics.PAYMENT_EVENTS, "Payment", payment.getId(), EventTypes.PAYMENT_SUCCEEDED,
				new PaymentSucceeded(payment.getId(), payment.getOrderId(), payment.getUserId(),
						payment.getMethod().name(), payment.getAmount(), payment.getReceiptNumber()));
	}

	private void markFailed(Payment payment, String reference, String reason) {
		payment.fail(reference, reason);
		events.publish(Topics.PAYMENT_EVENTS, "Payment", payment.getId(), EventTypes.PAYMENT_FAILED,
				new PaymentFailed(payment.getId(), payment.getOrderId(), payment.getUserId(),
						payment.getMethod().name(), reason));
	}

}
