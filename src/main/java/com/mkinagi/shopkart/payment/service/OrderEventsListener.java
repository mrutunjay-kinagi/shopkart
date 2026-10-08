package com.mkinagi.shopkart.payment.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.common.events.EventEnvelope;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderCancelled;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderDelivered;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderPlaced;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.IdempotentEventHandler;
import com.mkinagi.shopkart.common.events.Topics;

/**
 * The payment service's subscription to order events: opens and settles cash-on-delivery payments and
 * refunds payments of cancelled orders.
 */
@Component
public class OrderEventsListener {

	static final String CONSUMER = "payment-service";

	private final IdempotentEventHandler handler;

	private final PaymentService paymentService;

	public OrderEventsListener(IdempotentEventHandler handler, PaymentService paymentService) {
		this.handler = handler;
		this.paymentService = paymentService;
	}

	@KafkaListener(topics = Topics.ORDER_EVENTS, groupId = CONSUMER)
	public void onOrderEvent(String message) {
		EventEnvelope envelope = handler.parse(message);
		handler.handleOnce(CONSUMER, envelope, e -> {
			switch (e.eventType()) {
				case EventTypes.ORDER_PLACED -> {
					OrderPlaced p = handler.payload(e, OrderPlaced.class);
					if (PaymentMethod.COD.name().equals(p.paymentMethod())) {
						paymentService.openCashOnDelivery(p.orderId(), p.userId(), p.totalAmount());
					}
				}
				case EventTypes.ORDER_DELIVERED -> {
					OrderDelivered p = handler.payload(e, OrderDelivered.class);
					if (PaymentMethod.COD.name().equals(p.paymentMethod())) {
						paymentService.settleCashOnDelivery(p.orderId());
					}
				}
				case EventTypes.ORDER_CANCELLED -> paymentService
					.onOrderCancelled(handler.payload(e, OrderCancelled.class).orderId());
				default -> {
				}
			}
		});
	}

}
