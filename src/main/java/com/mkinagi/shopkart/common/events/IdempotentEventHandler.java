package com.mkinagi.shopkart.common.events;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Runs a consumer's handler at most once per (consumer, eventId). The marker row and the handler's
 * own writes commit in one transaction, so a crash after handling but before the Kafka offset commit
 * results in a harmless skipped duplicate rather than a double side effect.
 */
@Component
public class IdempotentEventHandler {

	private static final Logger log = LoggerFactory.getLogger(IdempotentEventHandler.class);

	private final ProcessedEventRepository processed;

	private final ObjectMapper objectMapper;

	public IdempotentEventHandler(ProcessedEventRepository processed, ObjectMapper objectMapper) {
		this.processed = processed;
		this.objectMapper = objectMapper;
	}

	public EventEnvelope parse(String json) {
		return objectMapper.readValue(json, EventEnvelope.class);
	}

	public <T> T payload(EventEnvelope envelope, Class<T> type) {
		return objectMapper.treeToValue(envelope.payload(), type);
	}

	@Transactional
	public void handleOnce(String consumer, EventEnvelope envelope, Consumer<EventEnvelope> handler) {
		ProcessedEvent.Key key = new ProcessedEvent.Key(consumer, envelope.eventId());
		if (processed.existsById(key)) {
			log.debug("Skipping duplicate event {} for {}", envelope.eventId(), consumer);
			return;
		}
		handler.accept(envelope);
		processed.save(new ProcessedEvent(consumer, envelope.eventId()));
	}

}
