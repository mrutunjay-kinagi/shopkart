package com.mkinagi.shopkart.common;

/**
 * Payment options offered at checkout. Shared by the order and payment modules.
 */
public enum PaymentMethod {

	CARD, UPI, NETBANKING, COD;

	public boolean isOnline() {
		return this != COD;
	}

}
