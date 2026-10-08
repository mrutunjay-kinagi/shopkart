package com.mkinagi.shopkart.user.service;

/**
 * What other modules may ask the user module. Keeping this narrow is what would let the user module
 * become a separate service behind an HTTP client with the same interface.
 */
public interface UserDirectory {

	UserContact contact(Long userId);

	ShippingAddress shippingAddress(Long userId, Long addressId);

	record UserContact(Long id, String email, String fullName) {
	}

	record ShippingAddress(String recipientName, String phone, String line1, String line2, String city, String state,
			String postalCode, String country) {
	}

}
