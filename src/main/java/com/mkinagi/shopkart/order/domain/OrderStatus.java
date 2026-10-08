package com.mkinagi.shopkart.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle as an explicit state machine; {@link Order#transitionTo} rejects any move not listed
 * here, so an event arriving late or twice cannot push an order backwards.
 *
 * <pre>
 * PENDING_PAYMENT -> CONFIRMED -> SHIPPED -> OUT_FOR_DELIVERY -> DELIVERED
 *        \               \
 *         +-> CANCELLED <-+
 * </pre>
 */
public enum OrderStatus {

	PENDING_PAYMENT, CONFIRMED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

	public Set<OrderStatus> next() {
		return switch (this) {
			case PENDING_PAYMENT -> EnumSet.of(CONFIRMED, CANCELLED);
			case CONFIRMED -> EnumSet.of(SHIPPED, CANCELLED);
			case SHIPPED -> EnumSet.of(OUT_FOR_DELIVERY, DELIVERED);
			case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
			case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
		};
	}

	public boolean canTransitionTo(OrderStatus target) {
		return next().contains(target);
	}

	public boolean isCancellableByCustomer() {
		return this == PENDING_PAYMENT || this == CONFIRMED;
	}

}
