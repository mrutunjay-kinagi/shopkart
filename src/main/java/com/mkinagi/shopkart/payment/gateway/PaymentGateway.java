package com.mkinagi.shopkart.payment.gateway;

import java.math.BigDecimal;

import com.mkinagi.shopkart.common.PaymentMethod;

/**
 * Port to an external payment processor (Razorpay, Stripe, PayU ...). The application only ever sees
 * opaque tokens and references, which keeps it out of PCI-DSS card-data scope.
 */
public interface PaymentGateway {

	String name();

	ChargeResult charge(ChargeRequest request);

	RefundResult refund(String gatewayReference, BigDecimal amount);

	/** Verifies that a webhook body was really sent by the gateway. */
	boolean verifyWebhookSignature(byte[] body, String signature);

	record ChargeRequest(String merchantReference, BigDecimal amount, String currency, PaymentMethod method,
			String cardToken, String upiVpa, String bankCode) {
	}

	enum Outcome {

		SUCCEEDED, PENDING, FAILED

	}

	record ChargeResult(Outcome outcome, String gatewayReference, String instrumentSummary, String failureReason) {
	}

	record RefundResult(boolean succeeded, String refundReference) {
	}

}
