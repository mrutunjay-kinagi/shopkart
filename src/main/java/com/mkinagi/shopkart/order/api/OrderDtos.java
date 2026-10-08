package com.mkinagi.shopkart.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.order.domain.Order;
import com.mkinagi.shopkart.order.domain.OrderItem;
import com.mkinagi.shopkart.order.domain.OrderStatus;
import com.mkinagi.shopkart.order.domain.OrderStatusChange;
import com.mkinagi.shopkart.order.domain.ShippingAddress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrderDtos {

	static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private OrderDtos() {
	}

	public record AddressInput(@NotBlank @Size(max = 120) String recipientName,
			@NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must be a valid phone number") String phone,
			@NotBlank @Size(max = 200) String line1, @Size(max = 200) String line2,
			@NotBlank @Size(max = 80) String city, @NotBlank @Size(max = 80) String state,
			@NotBlank @Pattern(regexp = "^[0-9A-Za-z -]{3,12}$", message = "must be a valid postal code") String postalCode,
			@Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String country) {
	}

	/**
	 * Only the delivery address and payment method: the products, quantities and prices come from the
	 * server-side cart and catalogue, so a client cannot tamper with what it is charged.
	 */
	public record CheckoutRequest(Long addressId, @Valid AddressInput shippingAddress,
			@NotNull PaymentMethod paymentMethod) {

		@AssertTrue(message = "provide exactly one of addressId or shippingAddress")
		public boolean isAddressSpecifiedOnce() {
			return (addressId == null) != (shippingAddress == null);
		}

	}

	public record OrderItemResponse(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
			BigDecimal lineTotal) {

		static OrderItemResponse from(OrderItem item) {
			return new OrderItemResponse(item.getProductId(), item.getSku(), item.getProductName(),
					item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
		}

	}

	public record OrderResponse(Long id, String orderNumber, OrderStatus status, PaymentMethod paymentMethod,
			List<OrderItemResponse> items, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal totalAmount,
			String currency, ShippingAddress shippingAddress, Instant paymentDueAt, Instant placedAt,
			String nextAction) {

		public static OrderResponse from(Order order) {
			String next = switch (order.getStatus()) {
				case PENDING_PAYMENT -> "PAY";
				case CONFIRMED, SHIPPED, OUT_FOR_DELIVERY -> "TRACK";
				case DELIVERED, CANCELLED -> "NONE";
			};
			return new OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
					order.getPaymentMethod(), order.getItems().stream().map(OrderItemResponse::from).toList(),
					order.getSubtotal(), order.getShippingFee(), order.getTotalAmount(), order.getCurrency(),
					order.getShippingAddress(), order.getPaymentDueAt(), order.getCreatedAt(), next);
		}

	}

	public record OrderSummary(Long id, String orderNumber, OrderStatus status, int itemCount, BigDecimal totalAmount,
			Instant placedAt) {

		public static OrderSummary from(Order order) {
			return new OrderSummary(order.getId(), order.getOrderNumber(), order.getStatus(),
					order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(), order.getTotalAmount(),
					order.getCreatedAt());
		}

	}

	public record TimelineEntry(OrderStatus status, String note, Instant at) {

		static TimelineEntry from(OrderStatusChange change) {
			return new TimelineEntry(change.getStatus(), change.getNote(), change.getChangedAt());
		}

	}

	public record TrackingResponse(String orderNumber, OrderStatus status, String carrier, String trackingNumber,
			LocalDate estimatedDelivery, List<TimelineEntry> timeline) {

		public static TrackingResponse from(Order order) {
			LocalDate eta = switch (order.getStatus()) {
				case CONFIRMED -> LocalDate.ofInstant(order.getCreatedAt(), IST).plusDays(5);
				case SHIPPED, OUT_FOR_DELIVERY -> LocalDate.ofInstant(lastChange(order), IST).plusDays(
						order.getStatus() == OrderStatus.SHIPPED ? 3 : 0);
				default -> null;
			};
			return new TrackingResponse(order.getOrderNumber(), order.getStatus(), order.getCarrier(),
					order.getTrackingNumber(), eta, order.getHistory().stream().map(TimelineEntry::from).toList());
		}

		private static Instant lastChange(Order order) {
			return order.getHistory().get(order.getHistory().size() - 1).getChangedAt();
		}

	}

	public record CancelRequest(@Size(max = 200) String reason) {
	}

	public record StatusUpdateRequest(@NotNull OrderStatus status, @Size(max = 40) String carrier,
			@Size(max = 40) String trackingNumber, @Size(max = 200) String note) {
	}

}
