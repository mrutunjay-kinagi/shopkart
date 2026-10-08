package com.mkinagi.shopkart.order.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.common.PageResponse;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderResponse;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderSummary;
import com.mkinagi.shopkart.order.api.OrderDtos.StatusUpdateRequest;
import com.mkinagi.shopkart.order.domain.OrderStatus;
import com.mkinagi.shopkart.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Validated
@Tag(name = "Admin: Orders", description = "Fulfilment operations (ADMIN role)")
@SecurityRequirement(name = "bearerAuth")
public class AdminOrderController {

	private final OrderService orderService;

	public AdminOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	@Operation(summary = "List orders, optionally by status")
	public PageResponse<OrderSummary> list(@RequestParam(required = false) OrderStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return orderService.adminList(status, page, size);
	}

	@PatchMapping("/{orderId}/status")
	@Operation(summary = "Advance fulfilment: SHIPPED (with carrier and tracking number), OUT_FOR_DELIVERY, DELIVERED or CANCELLED")
	public OrderResponse updateStatus(@PathVariable Long orderId, @Valid @RequestBody StatusUpdateRequest request) {
		return orderService.updateStatus(orderId, request);
	}

}
