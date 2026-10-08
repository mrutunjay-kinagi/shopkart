package com.mkinagi.shopkart.user.api;

import com.mkinagi.shopkart.user.domain.Address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AddressDtos {

	private AddressDtos() {
	}

	public record AddressRequest(@NotBlank @Size(max = 40) String label, @NotBlank @Size(max = 120) String recipientName,
			@NotBlank @Pattern(regexp = AuthDtos.PHONE_RULE, message = "must be a valid phone number") String phone,
			@NotBlank @Size(max = 200) String line1, @Size(max = 200) String line2,
			@NotBlank @Size(max = 80) String city, @NotBlank @Size(max = 80) String state,
			@NotBlank @Pattern(regexp = "^[0-9A-Za-z -]{3,12}$", message = "must be a valid postal code") String postalCode,
			@Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String country,
			Boolean makeDefault) {

		public boolean isMakeDefault() {
			return Boolean.TRUE.equals(makeDefault);
		}

	}

	public record AddressResponse(Long id, String label, String recipientName, String phone, String line1, String line2,
			String city, String state, String postalCode, String country, boolean isDefault) {

		public static AddressResponse from(Address a) {
			return new AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(), a.getLine1(),
					a.getLine2(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(), a.isDefaultAddress());
		}

	}

}
