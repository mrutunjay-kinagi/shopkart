package com.mkinagi.shopkart.common.events;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Polls unpublished outbox rows and sends them to Kafka in id order, keyed by aggregate id so that all
 * events of one order land on the same partition and are consumed in order. Delivery is at-least-once;
 * consumers de-duplicate using {@code eventId}.
 */
@Component
@ConditionalOnProperty(name = "shopkart.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private static final int BATCH_SIZE = 100;

	private final OutboxEventRepository outbox;

	private final KafkaTemplate<String, String> kafka;

	private final TransactionTemplate tx;

	private final Counter published;

	private final Counter failures;

	public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
			MeterRegistry meters) {
		this.outbox = outbox;
		this.kafka = kafka;
		this.tx = tx;
		this.published = meters.counter("shopkart.outbox.published");
		this.failures = meters.counter("shopkart.outbox.failures");
	}

	@Scheduled(fixedDelayString = "${shopkart.outbox.poll-interval:500ms}")
	public void relay() {
		Integer sent;
		do {
			sent = tx.execute(status -> relayBatch());
		}
		while (sent != null && sent == BATCH_SIZE);
	}

	private int relayBatch() {
		List<OutboxEvent> batch = outbox.claimUnpublished(BATCH_SIZE);
		int count = 0;
		for (OutboxEvent event : batch) {
			try {
				kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get(10, TimeUnit.SECONDS);
				event.markPublished(Instant.now());
				published.increment();
				count++;
			}
			catch (Exception ex) {
				if (ex instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				// Stop at the first failure to preserve ordering; the row is retried on the next poll.
				event.recordFailedAttempt();
				failures.increment();
				log.warn("Failed to relay outbox event {} ({}), attempt {}", event.getEventId(), event.getEventType(),
						event.getAttempts(), ex);
				break;
			}
		}
		return count;
	}

}
