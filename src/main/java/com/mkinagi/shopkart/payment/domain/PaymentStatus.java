package com.mkinagi.shopkart.payment.domain;

public enum PaymentStatus {

	/** Waiting for the gateway (e.g. UPI collect request) or for cash collection on delivery. */
	PENDING,

	SUCCEEDED,

	FAILED,

	REFUNDED

}
