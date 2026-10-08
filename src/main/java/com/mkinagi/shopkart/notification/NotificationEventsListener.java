package com.mkinagi.shopkart.notification;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.mkinagi.shopkart.common.events.EventEnvelope;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderCancelled;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderConfirmed;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderDelivered;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderPlaced;
import com.mkinagi.shopkart.common.events.EventPayloads.OrderShipped;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentFailed;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentRefunded;
import com.mkinagi.shopkart.common.events.EventPayloads.PaymentSucceeded;
import com.mkinagi.shopkart.common.events.EventPayloads.UserRegistered;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.IdempotentEventHandler;
import com.mkinagi.shopkart.common.events.Topics;

/**
 * The notification service of the HLD: a pure Kafka consumer that turns business events into e-mails.
 * It owns no business state, so it can be scaled or redeployed independently of the order flow.
 */
@Component
public class NotificationEventsListener {

	static final String CONSUMER = "notification-service";

	private final IdempotentEventHandler handler;

	private final NotificationService notifications;

	public NotificationEventsListener(IdempotentEventHandler handler, NotificationService notifications) {
		this.handler = handler;
		this.notifications = notifications;
	}

	@KafkaListener(topics = { Topics.USER_EVENTS, Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS }, groupId = CONSUMER)
	public void onEvent(String message) {
		EventEnvelope envelope = handler.parse(message);
		handler.handleOnce(CONSUMER, envelope, e -> {
			switch (e.eventType()) {
				case EventTypes.USER_REGISTERED -> {
					UserRegistered p = handler.payload(e, UserRegistered.class);
					notifications.sendToUser(p.userId(), EmailTemplates::welcome);
				}
				case EventTypes.ORDER_PLACED -> {
					OrderPlaced p = handler.payload(e, OrderPlaced.class);
					notifications.sendToUser(p.userId(), name -> EmailTemplates.orderPlaced(name, p));
				}
				case EventTypes.ORDER_CONFIRMED -> {
					OrderConfirmed p = handler.payload(e, OrderConfirmed.class);
					notifications.sendToUser(p.userId(), name -> EmailTemplates.orderConfirmed(name, p.orderNumber()));
				}
				case EventTypes.ORDER_SHIPPED -> {
					OrderShipped p = handler.payload(e, OrderShipped.class);
					notifications.sendToUser(p.userId(),
							name -> EmailTemplates.orderShipped(name, p.orderNumber(), p.carrier(), p.trackingNumber()));
				}
				case EventTypes.ORDER_DELIVERED -> {
					OrderDelivered p = handler.payload(e, OrderDelivered.class);
					notifications.sendToUser(p.userId(), name -> EmailTemplates.orderDelivered(name, p.orderNumber()));
				}
				case EventTypes.ORDER_CANCELLED -> {
					OrderCancelled p = handler.payload(e, OrderCancelled.class);
					if (!"PAYMENT_AFTER_CANCELLATION".equals(p.reason())) {
						notifications.sendToUser(p.userId(),
								name -> EmailTemplates.orderCancelled(name, p.orderNumber(), p.reason()));
					}
				}
				case EventTypes.PAYMENT_SUCCEEDED -> {
					PaymentSucceeded p = handler.payload(e, PaymentSucceeded.class);
					notifications.sendToUser(p.userId(),
							name -> EmailTemplates.paymentReceipt(name, p.receiptNumber(), p.amount(), p.method()));
				}
				case EventTypes.PAYMENT_FAILED -> {
					PaymentFailed p = handler.payload(e, PaymentFailed.class);
					notifications.sendToUser(p.userId(), name -> EmailTemplates.paymentFailed(name, p.reason()));
				}
				case EventTypes.PAYMENT_REFUNDED -> {
					PaymentRefunded p = handler.payload(e, PaymentRefunded.class);
					notifications.sendToUser(p.userId(), name -> EmailTemplates.refund(name, p.amount()));
				}
				default -> {
				}
			}
		});
	}

}
