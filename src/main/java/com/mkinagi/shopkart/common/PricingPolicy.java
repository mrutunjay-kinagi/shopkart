package com.mkinagi.shopkart.common;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * Shipping rules shared by cart preview and checkout so both always agree. Catalogue prices are
 * GST-inclusive, so no tax line is added on top.
 */
@Component
public class PricingPolicy {

	static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("499.00");

	static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("49.00");

	public BigDecimal shippingFee(BigDecimal subtotal) {
		if (subtotal.signum() == 0 || subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
			return BigDecimal.ZERO.setScale(2);
		}
		return STANDARD_SHIPPING_FEE;
	}

}
