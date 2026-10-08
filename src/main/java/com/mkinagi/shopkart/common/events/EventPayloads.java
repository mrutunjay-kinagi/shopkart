package com.mkinagi.shopkart.common.events;

import java.math.BigDecimal;
import java.util.List;

/**
 * The published event contract between modules. Modules depend on these records, never on each
 * other's entities, so any module can later be extracted into its own service without changing the
 * wire format.
 */
public final class EventPayloads {

	private EventPayloads() {
	}

	public record UserRegistered(Long userId, String email, String fullName) {
	}

	public record CartItemAdded(Long userId, Long productId, int quantity) {
	}

	public record OrderLine(Long productId, String sku, String name, int quantity, BigDecimal unitPrice) {
	}

	public record OrderPlaced(Long orderId, String orderNumber, Long userId, String paymentMethod,
			BigDecimal totalAmount, List<OrderLine> items) {
	}

	public record OrderConfirmed(Long orderId, String orderNumber, Long userId) {
	}

	public record OrderCancelled(Long orderId, String orderNumber, Long userId, String reason) {
	}

	public record OrderShipped(Long orderId, String orderNumber, Long userId, String carrier, String trackingNumber) {
	}

	public record OrderDelivered(Long orderId, String orderNumber, Long userId, String paymentMethod) {
	}

	public record PaymentSucceeded(Long paymentId, Long orderId, Long userId, String method, BigDecimal amount,
			String receiptNumber) {
	}

	public record PaymentFailed(Long paymentId, Long orderId, Long userId, String method, String reason) {
	}

	public record PaymentRefunded(Long paymentId, Long orderId, Long userId, BigDecimal amount) {
	}

}
