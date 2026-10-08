package com.mkinagi.shopkart.order.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases stock held by orders that were never paid. Each order is expired in its own transaction so a
 * concurrent payment confirmation (detected through the order's {@code @Version}) only skips that order.
 */
@Component
public class OrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

	private final OrderService orderService;

	public OrderExpiryJob(OrderService orderService) {
		this.orderService = orderService;
	}

	@Scheduled(fixedDelayString = "${shopkart.orders.expiry-check-interval:60s}")
	public void expireUnpaidOrders() {
		Instant now = Instant.now();
		int expired = 0;
		for (Long orderId : orderService.findExpiredUnpaid(now, 200)) {
			try {
				if (orderService.expire(orderId, now)) {
					expired++;
				}
			}
			catch (OptimisticLockingFailureException ex) {
				log.info("Order {} changed while expiring (probably just paid); skipped", orderId);
			}
		}
		if (expired > 0) {
			log.info("Expired {} unpaid orders", expired);
		}
	}

}
