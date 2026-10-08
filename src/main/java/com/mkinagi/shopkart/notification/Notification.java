package com.mkinagi.shopkart.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit record of every message sent (or attempted), e.g. for customer-support look-ups.
 */
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false)
	private String channel;

	@Column(nullable = false)
	private String recipient;

	@Column(nullable = false)
	private String template;

	@Column(nullable = false)
	private String subject;

	@Column(nullable = false)
	private String status;

	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Notification() {
	}

	public Notification(Long userId, String recipient, String template, String subject, String status, String error) {
		this.userId = userId;
		this.channel = "EMAIL";
		this.recipient = recipient;
		this.template = template;
		this.subject = subject;
		this.status = status;
		this.error = error;
		this.createdAt = Instant.now();
	}

	public Long getUserId() {
		return userId;
	}

	public String getTemplate() {
		return template;
	}

	public String getSubject() {
		return subject;
	}

	public String getStatus() {
		return status;
	}

}
