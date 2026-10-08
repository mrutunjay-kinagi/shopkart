package com.mkinagi.shopkart.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mkinagi.shopkart.cart.service.CartService;
import com.mkinagi.shopkart.catalog.service.InventoryService;
import com.mkinagi.shopkart.catalog.service.InventoryService.ReservedLine;
import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.common.PricingPolicy;
import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.common.events.DomainEventPublisher;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderConfirmed;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderLine;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderPlaced;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.Topics;
import com.mkinagi.shopkart.config.ShopkartProperties;
import com.mkinagi.shopkart.order.api.OrderDtos.AddressInput;
import com.mkinagi.shopkart.order.api.OrderDtos.CheckoutRequest;
import com.mkinagi.shopkart.order.api.OrderDtos.OrderResponse;
import com.mkinagi.shopkart.order.domain.Order;
import com.mkinagi.shopkart.order.domain.OrderRepository;
import com.mkinagi.shopkart.order.domain.OrderStatus;
import com.mkinagi.shopkart.order.domain.ShippingAddress;
import com.mkinagi.shopkart.user.service.UserDirectory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Turns the user's cart into an order in one MySQL transaction: lock and decrement stock, snapshot
 * prices, persist the order and its outbox event. Either all of it commits or none of it does.
 */
@Service
public class CheckoutService {

	private final OrderRepository orders;

	private final CartService carts;

	private final InventoryService inventory;

	private final UserDirectory users;

	private final PricingPolicy pricing;

	private final OrderNumberGenerator orderNumbers;

	private final DomainEventPublisher events;

	private final TransactionTemplate tx;

	private final ShopkartProperties.Orders settings;

	private final MeterRegistry meters;

	private final Timer checkoutTimer;

	public CheckoutService(OrderRepository orders, CartService carts, InventoryService inventory,
			UserDirectory users, PricingPolicy pricing, OrderNumberGenerator orderNumbers, DomainEventPublisher events,
			TransactionTemplate tx, ShopkartProperties properties, MeterRegistry meters) {
		this.orders = orders;
		this.carts = carts;
		this.inventory = inventory;
		this.users = users;
		this.pricing = pricing;
		this.orderNumbers = orderNumbers;
		this.events = events;
		this.tx = tx;
		this.settings = properties.orders();
		this.meters = meters;
		this.checkoutTimer = Timer.builder("shopkart.checkout.duration")
			.description("Time to place an order")
			.publishPercentiles(0.5, 0.95, 0.99)
			.register(meters);
	}

	/**
	 * @param idempotencyKey optional client-generated key; retrying with the same key (e.g. after a network
	 * timeout) returns the original order instead of placing a second one
	 */
	public CheckoutResult checkout(Long userId, CheckoutRequest request, String idempotencyKey) {
		if (idempotencyKey != null) {
			CheckoutResult replay = findReplay(userId, idempotencyKey);
			if (replay != null) {
				return replay;
			}
		}
		Map<Long, Integer> cart = carts.items(userId);
		if (cart.isEmpty()) {
			throw ApiException.unprocessable("CART_EMPTY", "Your cart is empty");
		}
		ShippingAddress address = resolveAddress(userId, request);
		Order order;
		try {
			order = checkoutTimer
				.record(() -> tx.execute(status -> placeOrder(userId, cart, address, request.paymentMethod(),
						idempotencyKey)));
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent retry with the same idempotency key committed first.
			CheckoutResult replay = idempotencyKey != null ? findReplay(userId, idempotencyKey) : null;
			if (replay != null) {
				return replay;
			}
			throw ex;
		}
		carts.clear(userId);
		meters.counter("shopkart.orders.placed", "paymentMethod", request.paymentMethod().name()).increment();
		return new CheckoutResult(OrderResponse.from(order), false);
	}

	private Order placeOrder(Long userId, Map<Long, Integer> cart, ShippingAddress address, PaymentMethod method,
			String idempotencyKey) {
		List<ReservedLine> lines = inventory.reserve(cart);
		BigDecimal subtotal = lines.stream().map(ReservedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		Instant dueAt = method.isOnline() ? Instant.now().plus(settings.paymentWindow()) : null;

		Order order = new Order(orderNumbers.next(), userId, method, address, idempotencyKey, dueAt);
		lines.forEach(l -> order.addItem(l.productId(), l.sku(), l.name(), l.unitPrice(), l.quantity()));
		order.price(subtotal, pricing.shippingFee(subtotal));
		if (!method.isOnline()) {
			order.transitionTo(OrderStatus.CONFIRMED, "Cash on delivery order confirmed");
		}
		orders.saveAndFlush(order);

		events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_PLACED,
				new OrderPlaced(order.getId(), order.getOrderNumber(), userId, method.name(), order.getTotalAmount(),
						lines.stream()
							.map(l -> new OrderLine(l.productId(), l.sku(), l.name(), l.quantity(), l.unitPrice()))
							.toList()));
		if (order.getStatus() == OrderStatus.CONFIRMED) {
			events.publish(Topics.ORDER_EVENTS, "Order", order.getId(), EventTypes.ORDER_CONFIRMED,
					new OrderConfirmed(order.getId(), order.getOrderNumber(), userId));
		}
		return order;
	}

	private CheckoutResult findReplay(Long userId, String idempotencyKey) {
		return tx.execute(status -> orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
			.map(o -> new CheckoutResult(OrderResponse.from(o), true))
			.orElse(null));
	}

	private ShippingAddress resolveAddress(Long userId, CheckoutRequest request) {
		if (request.addressId() != null) {
			UserDirectory.ShippingAddress a = users.shippingAddress(userId, request.addressId());
			return new ShippingAddress(a.recipientName(), a.phone(), a.line1(), a.line2(), a.city(), a.state(),
					a.postalCode(), a.country());
		}
		AddressInput a = request.shippingAddress();
		return new ShippingAddress(a.recipientName().trim(), a.phone(), a.line1().trim(), a.line2(), a.city().trim(),
				a.state().trim(), a.postalCode().trim(), a.country() != null ? a.country() : "IN");
	}

	public record CheckoutResult(OrderResponse order, boolean replayed) {
	}

}
