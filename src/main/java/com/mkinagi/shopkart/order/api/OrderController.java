package com.mkinagi.shopkart.order.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.common.PageResponse;
import com.mkinagi.shopkart.common.web.CurrentUser;
import com.mkinagi.shopkart.order.api.OrderDtos.CancelRequest;
import com.mkinagi.shopkart.order.api.OrderDtos.CheckoutRequest;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderResponse;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderSummary;
import com.mkinagi.shopkart.order.api.OrderDtos.TrackingResponse;
import com.mkinagi.shopkart.order.service.CheckoutService;
import com.mkinagi.shopkart.order.service.CheckoutService.CheckoutResult;
import com.mkinagi.shopkart.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Orders", description = "Checkout, order history and tracking")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

	private final CheckoutService checkoutService;

	private final OrderService orderService;

	public OrderController(CheckoutService checkoutService, OrderService orderService) {
		this.checkoutService = checkoutService;
		this.orderService = orderService;
	}

	@PostMapping("/checkout")
	@Operation(summary = "Place an order from the current cart",
			description = "Returns 201 for a new order, or 200 with the original order when the Idempotency-Key was already used.")
	public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "Client-generated key that makes retries safe") @RequestHeader(name = "Idempotency-Key", required = false) @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$") String idempotencyKey,
			@Valid @RequestBody CheckoutRequest request) {
		CheckoutResult result = checkoutService.checkout(CurrentUser.id(jwt), request, idempotencyKey);
		if (result.replayed()) {
			return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.order());
		}
		return ResponseEntity.created(URI.create("/api/v1/orders/" + result.order().id())).body(result.order());
	}

	@GetMapping
	@Operation(summary = "Order history, newest first")
	public PageResponse<OrderSummary> history(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
		return orderService.history(CurrentUser.id(jwt), page, size);
	}

	@GetMapping("/{orderId}")
	@Operation(summary = "Order details and confirmation")
	public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
		return orderService.get(CurrentUser.id(jwt), orderId);
	}

	@GetMapping("/{orderId}/tracking")
	@Operation(summary = "Delivery status, carrier, tracking number and timeline")
	public TrackingResponse tracking(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
		return orderService.tracking(CurrentUser.id(jwt), orderId);
	}

	@PostMapping("/{orderId}/cancel")
	@Operation(summary = "Cancel an order that has not shipped; paid orders are refunded")
	public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
			@Valid @RequestBody(required = false) CancelRequest request) {
		return orderService.cancelByCustomer(CurrentUser.id(jwt), orderId, request != null ? request.reason() : null);
	}

}
