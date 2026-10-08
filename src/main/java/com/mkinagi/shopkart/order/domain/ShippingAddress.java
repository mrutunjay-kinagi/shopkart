package com.mkinagi.shopkart.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Address copied onto the order at checkout, so later edits to the address book do not rewrite history.
 */
@Embeddable
public record ShippingAddress(@Column(name = "ship_name") String recipientName,
		@Column(name = "ship_phone") String phone, @Column(name = "ship_line1") String line1,
		@Column(name = "ship_line2") String line2, @Column(name = "ship_city") String city,
		@Column(name = "ship_state") String state, @Column(name = "ship_postal_code") String postalCode,
		@Column(name = "ship_country") String country) {
}
