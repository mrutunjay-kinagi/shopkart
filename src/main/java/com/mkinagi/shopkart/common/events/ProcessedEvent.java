package com.mkinagi.shopkart.common.events;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

	@EmbeddedId
	private Key key;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedEvent() {
	}

	public ProcessedEvent(String consumer, String eventId) {
		this.key = new Key(consumer, eventId);
		this.processedAt = Instant.now();
	}

	@Embeddable
	public record Key(@Column(name = "consumer") String consumer, @Column(name = "event_id") String eventId)
			implements Serializable {
	}

}
