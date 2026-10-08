package com.mkinagi.shopkart.common.events;

/**
 * Event type identifiers carried in every envelope.
 */
public final class EventTypes {

	public static final String USER_REGISTERED = "UserRegistered";

	public static final String CART_ITEM_ADDED = "CartItemAdded";

	public static final String ORDER_PLACED = "OrderPlaced";

	public static final String ORDER_CONFIRMED = "OrderConfirmed";

	public static final String ORDER_CANCELLED = "OrderCancelled";

	public static final String ORDER_SHIPPED = "OrderShipped";

	public static final String ORDER_DELIVERED = "OrderDelivered";

	public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";

	public static final String PAYMENT_FAILED = "PaymentFailed";

	public static final String PAYMENT_REFUNDED = "PaymentRefunded";

	private EventTypes() {
	}

}
