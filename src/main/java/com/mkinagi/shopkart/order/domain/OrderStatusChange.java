package com.mkinagi.shopkart.order.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One entry of the tracking timeline.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	private String note;

	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;

	protected OrderStatusChange() {
	}

	OrderStatusChange(Order order, OrderStatus status, String note, Instant changedAt) {
		this.order = order;
		this.status = status;
		this.note = note;
		this.changedAt = changedAt;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public String getNote() {
		return note;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

}
