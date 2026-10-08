package com.mkinagi.shopkart.order.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mkinagi.shopkart.catalog.service.InventoryService;
import com.mkinagi.shopkart.common.PageResponse;
import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.common.events.DomainEventPublisher;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderCancelled;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderConfirmed;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderDelivered;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderShipped;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.Topics;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderResponse;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderSummary;
import com.mkinagi.shopkart.order.api.OrderDtos.StatusUpdateRequest;
import com.mkinagi.shopkart.order.api.OrderDtos.TrackingResponse;
import com.mkinagi.shopkart.order.domain.Order;
import com.mkinagi.shopkart.order.domain.OrderItem;
import com.mkinagi.shopkart.order.domain.OrderRepository;
import com.mkinagi.shopkart.order.domain.OrderStatus;

/**
 * Order history, tracking, cancellation and the status changes driven by payments and fulfilment.
 */
@Service
public class OrderService implements OrderLookup {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private final OrderRepository orders;

	private final InventoryService inventory;

	private final DomainEventPublisher events;

	public OrderService(OrderRepository orders, InventoryService inventory, DomainEventPublisher events) {
		this.orders = orders;
		this.inventory = inventory;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderSummary> history(Long userId, int page, int size) {
		return PageResponse.of(orders.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
				OrderSummary::from);
	}

	@Transactional(readOnly = true)
	public OrderResponse get(Long userId, Long orderId) {
		return OrderResponse.from(loadOwned(userId, orderId));
	}

	@Transactional(readOnly = true)
	public TrackingResponse tracking(Long userId, Long orderId) {
		return TrackingResponse.from(loadOwned(userId, orderId));
	}

	@Transactional
	public OrderResponse cancelByCustomer(Long userId, Long orderId, String reason) {
		Order order = loadOwned(userId, orderId);
		if (!order.getStatus().isCancellableByCustomer()) {
			throw ApiException.conflict("ORDER_NOT_CANCELLABLE",
					"Order " + order.getOrderNumber() + " has already been " + order.getStatus().name().toLowerCase());
		}
		cancel(order, reason != null && !reason.isBlank() ? "Cancelled by customer: " + reason.trim()
				: "Cancelled by customer");
		return OrderResponse.from(order);
	}

	/** Consumer of {@code PaymentSucceeded}. */
	@Transactional
	public void onPaymentSucceeded(Long orderId, String receiptNumber) {
		Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
		switch (order.getStatus()) {
			case PENDING_PAYMENT -> {
				order.transitionTo(OrderStatus.CONFIRMED, "Payment received (receipt " + receiptNumber + ")");
				events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_CONFIRMED,
						new OrderConfirmed(order.getId(), order.getOrderNumber(), order.getUserId()));
			}
			case CANCELLED -> {
				// Payment completed after the order expired: re-announce the cancellation so the payment
				// service refunds this capture.
				log.warn("Payment {} captured for cancelled order {}; requesting refund", receiptNumber,
						order.getOrderNumber());
				order.note("Payment " + receiptNumber + " received after cancellation; refund initiated");
				events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_CANCELLED,
						new OrderCancelled(order.getId(), order.getOrderNumber(), order.getUserId(),
								"PAYMENT_AFTER_CANCELLATION"));
			}
			default -> log.debug("Ignoring payment for order {} in status {}", order.getOrderNumber(),
					order.getStatus());
		}
	}

	/** Consumer of {@code PaymentFailed}: the order stays payable until its payment window closes. */
	@Transactional
	public void onPaymentFailed(Long orderId, String reason) {
		orders.findById(orderId)
			.filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT)
			.ifPresent(o -> o.note("Payment attempt failed: " + reason));
	}

	@Transactional
	public OrderResponse updateStatus(Long orderId, StatusUpdateRequest request) {
		Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
		OrderStatus target = request.status();
		switch (target) {
			case SHIPPED -> {
				if (request.carrier() == null || request.trackingNumber() == null) {
					throw ApiException.badRequest("TRACKING_REQUIRED", "carrier and trackingNumber are required to ship");
				}
				order.assignShipment(request.carrier(), request.trackingNumber());
				order.transitionTo(target, note(request, "Shipped via " + request.carrier()));
				events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_SHIPPED,
						new OrderShipped(order.getId(), order.getOrderNumber(), order.getUserId(), request.carrier(),
								request.trackingNumber()));
			}
			case OUT_FOR_DELIVERY -> order.transitionTo(target, note(request, "Out for delivery"));
			case DELIVERED -> {
				order.transitionTo(target, note(request, "Delivered"));
				events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_DELIVERED,
						new OrderDelivered(order.getId(), order.getOrderNumber(), order.getUserId(),
								order.getPaymentMethod().name()));
			}
			case CANCELLED -> {
				if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
					throw ApiException.conflict("INVALID_ORDER_TRANSITION",
							"Order " + order.getOrderNumber() + " can no longer be cancelled");
				}
				cancel(order, note(request, "Cancelled by ShopKart"));
			}
			default -> throw ApiException.badRequest("INVALID_ORDER_TRANSITION",
					"Status " + target + " cannot be set manually");
		}
		return OrderResponse.from(order);
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderSummary> adminList(OrderStatus status, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size);
		return PageResponse.of(status != null ? orders.findByStatusOrderByCreatedAtDesc(status, pageable)
				: orders.findAllByOrderByCreatedAtDesc(pageable), OrderSummary::from);
	}

	@Transactional(readOnly = true)
	public List<Long> findExpiredUnpaid(Instant now, int limit) {
		return orders.findByStatusAndPaymentDueAtBeforeOrderByPaymentDueAtAsc(OrderStatus.PENDING_PAYMENT, now,
				Limit.of(limit)).stream().map(Order::getId).toList();
	}

	/** Cancels one unpaid order whose payment window has closed; a no-op if it was paid meanwhile. */
	@Transactional
	public boolean expire(Long orderId, Instant now) {
		Order order = orders.findById(orderId).orElse(null);
		if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentDueAt() == null
				|| order.getPaymentDueAt().isAfter(now)) {
			return false;
		}
		cancel(order, "Cancelled automatically: payment not received in time");
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public OrderPaymentView forPayment(Long orderId) {
		Order o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
		return new OrderPaymentView(o.getId(), o.getOrderNumber(), o.getUserId(), o.getStatus(), o.getPaymentMethod(),
				o.getTotalAmount(), o.getCurrency(), o.getPaymentDueAt(),
				o.getItems()
					.stream()
					.map(i -> new Line(i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal()))
					.toList());
	}

	private void cancel(Order order, String note) {
		order.transitionTo(OrderStatus.CANCELLED, note);
		Map<Long, Integer> quantities = order.getItems()
			.stream()
			.collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
		inventory.release(quantities);
		events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_CANCELLED,
				new OrderCancelled(order.getId(), order.getOrderNumber(), order.getUserId(), note));
	}

	private Order loadOwned(Long userId, Long orderId) {
		// 404 rather than 403 for someone else's order, so ids cannot be probed.
		return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order", orderId));
	}

	private static String note(StatusUpdateRequest request, String fallback) {
		return request.note() != null && !request.note().isBlank() ? request.note().trim() : fallback;
	}

}
