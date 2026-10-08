package com.mkinagi.shopkart.user.domain;

import com.mkinagi.shopkart.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(nullable = false)
	private String label;

	@Column(name = "recipient_name", nullable = false)
	private String recipientName;

	@Column(nullable = false)
	private String phone;

	@Column(nullable = false)
	private String line1;

	private String line2;

	@Column(nullable = false)
	private String city;

	@Column(nullable = false)
	private String state;

	@Column(name = "postal_code", nullable = false)
	private String postalCode;

	@Column(nullable = false)
	private String country;

	@Column(name = "is_default", nullable = false)
	private boolean defaultAddress;

	protected Address() {
	}

	public Address(Long userId) {
		this.userId = userId;
	}

	public void update(String label, String recipientName, String phone, String line1, String line2, String city,
			String state, String postalCode, String country) {
		this.label = label;
		this.recipientName = recipientName;
		this.phone = phone;
		this.line1 = line1;
		this.line2 = line2;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
		this.country = country;
	}

	public void setDefaultAddress(boolean defaultAddress) {
		this.defaultAddress = defaultAddress;
	}

	public Long getUserId() {
		return userId;
	}

	public String getLabel() {
		return label;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public String getPhone() {
		return phone;
	}

	public String getLine1() {
		return line1;
	}

	public String getLine2() {
		return line2;
	}

	public String getCity() {
		return city;
	}

	public String getState() {
		return state;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getCountry() {
		return country;
	}

	public boolean isDefaultAddress() {
		return defaultAddress;
	}

}
