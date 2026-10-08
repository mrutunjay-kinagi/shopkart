package com.mkinagi.shopkart.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.payment.domain.Payment;
import com.mkinagi.shopkart.payment.domain.PaymentStatus;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {

	private PaymentDtos() {
	}

	/**
	 * Method-specific instrument for the order's chosen payment method. There is intentionally no field for
	 * a card number or CVV: cards are tokenised client-side by the gateway SDK.
	 */
	public record PayRequest(@Size(max = 64) String cardToken,
			@Pattern(regexp = "^[a-zA-Z0-9._-]{2,64}@[a-zA-Z]{2,32}$", message = "must be a valid UPI id") String upiVpa,
			@Pattern(regexp = "^[A-Za-z]{3,10}$", message = "must be a bank code such as HDFC") String bankCode) {
	}

	public record PaymentResponse(Long id, Long orderId, PaymentMethod method, PaymentStatus status, BigDecimal amount,
			String currency, String instrument, String failureReason, String receiptNumber, Instant paidAt,
			Instant createdAt) {

		public static PaymentResponse from(Payment p) {
			return new PaymentResponse(p.getId(), p.getOrderId(), p.getMethod(), p.getStatus(), p.getAmount(),
					p.getCurrency(), p.getInstrumentSummary(), p.getFailureReason(), p.getReceiptNumber(),
					p.getPaidAt(), p.getCreatedAt());
		}

	}

	public record ReceiptLine(String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
	}

	public record ReceiptResponse(String receiptNumber, String orderNumber, Instant paidAt, PaymentMethod method,
			String instrument, String gatewayReference, List<ReceiptLine> lines, BigDecimal totalPaid, String currency,
			String merchant) {
	}

	/** Body posted by MockPay to the webhook endpoint. */
	public record MockPayWebhook(String gatewayReference, String status, String reason) {
	}

}
