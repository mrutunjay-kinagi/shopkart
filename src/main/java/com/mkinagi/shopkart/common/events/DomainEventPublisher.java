package com.mkinagi.shopkart.common.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Records domain events in the transactional outbox. Because the row is written in the caller's
 * transaction, an event exists if and only if the business change was committed - there is no
 * "saved to MySQL but lost before Kafka" window.
 */
@Component
public class DomainEventPublisher {

	private final OutboxEventRepository outbox;

	private final ObjectMapper objectMapper;

	public DomainEventPublisher(OutboxEventRepository outbox, ObjectMapper objectMapper) {
		this.outbox = outbox;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(String topic, String aggregateType, Object aggregateId, String eventType, Object payload) {
		Instant now = Instant.now();
		String eventId = UUID.randomUUID().toString();
		EventEnvelope envelope = new EventEnvelope(eventId, eventType, aggregateType, String.valueOf(aggregateId), now,
				objectMapper.valueToTree(payload));
		outbox.save(new OutboxEvent(eventId, topic, aggregateType, String.valueOf(aggregateId), eventType,
				objectMapper.writeValueAsString(envelope), now));
	}

}
