package com.mkinagi.shopkart.common.events;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	/**
	 * Claims the next batch of unpublished events. {@code SKIP LOCKED} lets several application instances
	 * relay concurrently without blocking on, or double-sending, each other's rows.
	 */
	@NativeQuery("""
			SELECT * FROM outbox_events
			WHERE published_at IS NULL
			ORDER BY id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED""")
	List<OutboxEvent> claimUnpublished(@Param("limit") int limit);

	long countByPublishedAtIsNull();

}
