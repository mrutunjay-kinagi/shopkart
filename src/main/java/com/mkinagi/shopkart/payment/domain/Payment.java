package com.mkinagi.shopkart.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mkinagi.shopkart.common.BaseEntity;
import com.mkinagi.shopkart.common.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A payment attempt for an order. Only a masked instrument summary (e.g. "VISA **** 4242") is kept; card
 * numbers and CVVs never reach this service because the client tokenises them with the gateway.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	@Column(name = "order_id", nullable = false, updatable = false)
	private Long orderId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private String currency;

	@Column(nullable = false)
	private String gateway;

	@Column(name = "gateway_reference")
	private String gatewayReference;

	@Column(name = "instrument_summary")
	private String instrumentSummary;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "receipt_number", unique = true)
	private String receiptNumber;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Version
	private long version;

	protected Payment() {
	}

	public Payment(Long orderId, Long userId, PaymentMethod method, BigDecimal amount, String currency,
			String gateway) {
		this.orderId = orderId;
		this.userId = userId;
		this.method = method;
		this.amount = amount;
		this.currency = currency;
		this.gateway = gateway;
		this.status = PaymentStatus.PENDING;
	}

	public void awaitConfirmation(String gatewayReference, String instrumentSummary) {
		this.gatewayReference = gatewayReference;
		this.instrumentSummary = instrumentSummary;
	}

	public void succeed(String gatewayReference, String instrumentSummary, Instant at) {
		this.status = PaymentStatus.SUCCEEDED;
		this.gatewayReference = gatewayReference;
		this.instrumentSummary = instrumentSummary;
		this.paidAt = at;
		this.receiptNumber = "RCPT-" + String.format("%010d", getId());
	}

	public void fail(String gatewayReference, String reason) {
		this.status = PaymentStatus.FAILED;
		if (gatewayReference != null) {
			this.gatewayReference = gatewayReference;
		}
		this.failureReason = reason;
	}

	public void refunded() {
		this.status = PaymentStatus.REFUNDED;
	}

	public boolean isPending() {
		return status == PaymentStatus.PENDING;
	}

	public Long getOrderId() {
		return orderId;
	}

	public Long getUserId() {
		return userId;
	}

	public PaymentMethod getMethod() {
		return method;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getGateway() {
		return gateway;
	}

	public String getGatewayReference() {
		return gatewayReference;
	}

	public String getInstrumentSummary() {
		return instrumentSummary;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

}
