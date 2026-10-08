package com.mkinagi.shopkart.payment.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.common.web.CurrentUser;
import com.mkinagi.shopkart.payment.api.PaymentDtos.MockPayWebhook;
import com.mkinagi.shopkart.payment.api.PaymentDtos.PayRequest;
import com.mkinagi.shopkart.payment.api.PaymentDtos.PaymentResponse;
import com.mkinagi.shopkart.payment.api.PaymentDtos.ReceiptResponse;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway;
import com.mkinagi.shopkart.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tools.jackson.databind.ObjectMapper;

@RestController
@Tag(name = "Payments", description = "Pay for orders, view receipts, gateway webhooks")
public class PaymentController {

	private final PaymentService paymentService;

	private final PaymentGateway gateway;

	private final ObjectMapper objectMapper;

	public PaymentController(PaymentService paymentService, PaymentGateway gateway, ObjectMapper objectMapper) {
		this.paymentService = paymentService;
		this.gateway = gateway;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/api/v1/orders/{orderId}/payments")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Pay for an order with its chosen method",
			description = "CARD and NETBANKING settle immediately (201 SUCCEEDED/FAILED); UPI returns 202 PENDING until the gateway webhook confirms it.")
	public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
			@Valid @RequestBody PayRequest request) {
		PaymentResponse payment = paymentService.pay(CurrentUser.id(jwt), orderId, request);
		HttpStatus status = switch (payment.status()) {
			case PENDING -> HttpStatus.ACCEPTED;
			case FAILED -> HttpStatus.PAYMENT_REQUIRED;
			default -> HttpStatus.CREATED;
		};
		return ResponseEntity.status(status).body(payment);
	}

	@GetMapping("/api/v1/orders/{orderId}/payments")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Payment attempts for an order")
	public List<PaymentResponse> forOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
		return paymentService.forOrder(CurrentUser.id(jwt), orderId);
	}

	@GetMapping("/api/v1/payments/{paymentId}/receipt")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Receipt for a successful payment")
	public ReceiptResponse receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable Long paymentId) {
		return paymentService.receipt(CurrentUser.id(jwt), paymentId);
	}

	/**
	 * Public endpoint, authenticated by the HMAC signature over the raw body rather than by a JWT. The body
	 * is read as bytes so the signature is checked against exactly what the gateway sent.
	 */
	@PostMapping("/api/v1/payments/webhooks/mockpay")
	@Operation(summary = "MockPay webhook (HMAC-SHA256 signed)")
	public ResponseEntity<Void> mockPayWebhook(@RequestHeader(name = "X-MockPay-Signature", required = false) String signature,
			@RequestBody byte[] body) {
		if (!gateway.verifyWebhookSignature(body, signature)) {
			throw ApiException.unauthorized("INVALID_SIGNATURE", "Webhook signature verification failed");
		}
		paymentService.handleMockPayWebhook(objectMapper.readValue(body, MockPayWebhook.class));
		return ResponseEntity.noContent().build();
	}

}
