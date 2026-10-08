package com.mkinagi.shopkart.common.events;

/**
 * Kafka topic names. One topic per aggregate keeps per-aggregate ordering (records are keyed by
 * aggregate id) while letting consumers subscribe only to what they need.
 */
public final class Topics {

	public static final String USER_EVENTS = "shopkart.user.events";

	public static final String CART_EVENTS = "shopkart.cart.events";

	public static final String ORDER_EVENTS = "shopkart.order.events";

	public static final String PAYMENT_EVENTS = "shopkart.payment.events";

	private Topics() {
	}

}
