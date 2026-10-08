package com.mkinagi.shopkart.order.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.mkinagi.shopkart.common.events.EventEnvelope;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentFailed;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentSucceeded;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.IdempotentEventHandler;
import com.mkinagi.shopkart.common.events.Topics;

/**
 * The order service's subscription to payment outcomes (HLD: "Payment Service ... produces a message on
 * Kafka to notify the Order Management Service").
 */
@Component
public class PaymentEventsListener {

	static final String CONSUMER = "order-service";

	private final IdempotentEventHandler handler;

	private final OrderService orderService;

	public PaymentEventsListener(IdempotentEventHandler handler, OrderService orderService) {
		this.handler = handler;
		this.orderService = orderService;
	}

	@KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = CONSUMER)
	public void onPaymentEvent(String message) {
		EventEnvelope envelope = handler.parse(message);
		handler.handleOnce(CONSUMER, envelope, e -> {
			switch (e.eventType()) {
				case EventTypes.PAYMENT_SUCCEEDED -> {
					PaymentSucceeded p = handler.payload(e, PaymentSucceeded.class);
					orderService.onPaymentSucceeded(p.orderId(), p.receiptNumber());
				}
				case EventTypes.PAYMENT_FAILED -> {
					PaymentFailed p = handler.payload(e, PaymentFailed.class);
					orderService.onPaymentFailed(p.orderId(), p.reason());
				}
				default -> {
				}
			}
		});
	}

}
