package com.mkinagi.shopkart.common.events;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * Wire format of every Kafka record value. {@code eventId} lets consumers de-duplicate redeliveries.
 */
public record EventEnvelope(String eventId, String eventType, String aggregateType, String aggregateId,
		Instant occurredAt, JsonNode payload) {
}
