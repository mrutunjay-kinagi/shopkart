package com.mkinagi.shopkart.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.mkinagi.shopkart.common.BaseEntity;
import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.common.error.ApiException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Order aggregate root. All state changes go through {@link #transitionTo}, which enforces the
 * {@link OrderStatus} state machine and appends to the tracking timeline. {@code @Version} turns
 * concurrent updates (e.g. payment confirmation racing the expiry job) into an optimistic-lock failure
 * instead of a lost update.
 */
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

	@Column(name = "order_number", nullable = false, unique = true, updatable = false)
	private String orderNumber;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false)
	private PaymentMethod paymentMethod;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal shippingFee;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount;

	@Column(nullable = false)
	private String currency = "INR";

	@Column(name = "idempotency_key", updatable = false)
	private String idempotencyKey;

	@Embedded
	private ShippingAddress shippingAddress;

	private String carrier;

	@Column(name = "tracking_number")
	private String trackingNumber;

	@Column(name = "payment_due_at")
	private Instant paymentDueAt;

	@Version
	private long version;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<OrderItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("changedAt, id")
	private List<OrderStatusChange> history = new ArrayList<>();

	protected Order() {
	}

	public Order(String orderNumber, Long userId, PaymentMethod paymentMethod, ShippingAddress shippingAddress,
			String idempotencyKey, Instant paymentDueAt) {
		this.orderNumber = orderNumber;
		this.userId = userId;
		this.paymentMethod = paymentMethod;
		this.shippingAddress = shippingAddress;
		this.idempotencyKey = idempotencyKey;
		this.paymentDueAt = paymentDueAt;
		this.status = OrderStatus.PENDING_PAYMENT;
		this.subtotal = BigDecimal.ZERO;
		this.shippingFee = BigDecimal.ZERO;
		this.totalAmount = BigDecimal.ZERO;
		this.history.add(new OrderStatusChange(this, OrderStatus.PENDING_PAYMENT, "Order placed", Instant.now()));
	}

	public void addItem(Long productId, String sku, String name, BigDecimal unitPrice, int quantity) {
		items.add(new OrderItem(this, productId, sku, name, unitPrice, quantity));
	}

	public void price(BigDecimal subtotal, BigDecimal shippingFee) {
		this.subtotal = subtotal;
		this.shippingFee = shippingFee;
		this.totalAmount = subtotal.add(shippingFee);
	}

	public void transitionTo(OrderStatus target, String note) {
		if (!status.canTransitionTo(target)) {
			throw ApiException.conflict("INVALID_ORDER_TRANSITION",
					"Order " + orderNumber + " cannot move from " + status + " to " + target);
		}
		this.status = target;
		if (target != OrderStatus.PENDING_PAYMENT) {
			this.paymentDueAt = null;
		}
		history.add(new OrderStatusChange(this, target, note, Instant.now()));
	}

	/** Records a timeline note (e.g. a failed payment attempt) without changing status. */
	public void note(String note) {
		history.add(new OrderStatusChange(this, status, note, Instant.now()));
	}

	public void assignShipment(String carrier, String trackingNumber) {
		this.carrier = carrier;
		this.trackingNumber = trackingNumber;
	}

	public boolean isOwnedBy(Long userId) {
		return this.userId.equals(userId);
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public Long getUserId() {
		return userId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public PaymentMethod getPaymentMethod() {
		return paymentMethod;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getShippingFee() {
		return shippingFee;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public ShippingAddress getShippingAddress() {
		return shippingAddress;
	}

	public String getCarrier() {
		return carrier;
	}

	public String getTrackingNumber() {
		return trackingNumber;
	}

	public Instant getPaymentDueAt() {
		return paymentDueAt;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public List<OrderStatusChange> getHistory() {
		return history;
	}

}
