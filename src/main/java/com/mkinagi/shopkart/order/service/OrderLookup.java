package com.mkinagi.shopkart.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.order.domain.OrderStatus;

/**
 * Read-only view of an order offered to the payment module.
 */
public interface OrderLookup {

	OrderPaymentView forPayment(Long orderId);

	record OrderPaymentView(Long orderId, String orderNumber, Long userId, OrderStatus status,
			PaymentMethod paymentMethod, BigDecimal totalAmount, String currency, Instant paymentDueAt,
			List<Line> lines) {

		public boolean awaitingPayment(Instant now) {
			return status == OrderStatus.PENDING_PAYMENT && paymentDueAt != null && paymentDueAt.isAfter(now);
		}

	}

	record Line(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
	}

}
