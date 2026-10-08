package com.mkinagi.shopkart.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.user.api.AddressDtos.AddressRequest;
import com.mkinagi.shopkart.user.api.AddressDtos.AddressResponse;
import com.mkinagi.shopkart.user.api.AuthDtos.UpdateProfileRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.UserResponse;
import com.mkinagi.shopkart.user.domain.Address;
import com.mkinagi.shopkart.user.domain.AddressRepository;
import com.mkinagi.shopkart.user.domain.User;
import com.mkinagi.shopkart.user.domain.UserRepository;

/**
 * Profile and address book management. Also serves as the user module's public API
 * ({@link UserDirectory}) for other modules.
 */
@Service
public class UserService implements UserDirectory {

	private static final int MAX_ADDRESSES = 10;

	private final UserRepository users;

	private final AddressRepository addresses;

	public UserService(UserRepository users, AddressRepository addresses) {
		this.users = users;
		this.addresses = addresses;
	}

	@Transactional(readOnly = true)
	public UserResponse profile(Long userId) {
		return UserResponse.from(load(userId));
	}

	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
		User user = load(userId);
		user.updateProfile(request.fullName() != null ? request.fullName().trim() : null, request.phone());
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public List<AddressResponse> addresses(Long userId) {
		return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream().map(AddressResponse::from).toList();
	}

	@Transactional
	public AddressResponse addAddress(Long userId, AddressRequest request) {
		long existing = addresses.countByUserId(userId);
		if (existing >= MAX_ADDRESSES) {
			throw ApiException.unprocessable("ADDRESS_LIMIT_REACHED", "At most " + MAX_ADDRESSES + " addresses allowed");
		}
		Address address = new Address(userId);
		apply(address, request);
		address.setDefaultAddress(request.isMakeDefault() || existing == 0);
		addresses.save(address);
		if (address.isDefaultAddress()) {
			addresses.clearDefaultExcept(userId, address.getId());
		}
		return AddressResponse.from(address);
	}

	@Transactional
	public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
		Address address = loadAddress(userId, addressId);
		apply(address, request);
		if (request.isMakeDefault()) {
			address.setDefaultAddress(true);
			addresses.clearDefaultExcept(userId, address.getId());
		}
		return AddressResponse.from(address);
	}

	@Transactional
	public void deleteAddress(Long userId, Long addressId) {
		addresses.delete(loadAddress(userId, addressId));
	}

	@Override
	@Transactional(readOnly = true)
	public UserContact contact(Long userId) {
		User user = load(userId);
		return new UserContact(user.getId(), user.getEmail(), user.getFullName());
	}

	@Override
	@Transactional(readOnly = true)
	public ShippingAddress shippingAddress(Long userId, Long addressId) {
		Address a = loadAddress(userId, addressId);
		return new ShippingAddress(a.getRecipientName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(),
				a.getState(), a.getPostalCode(), a.getCountry());
	}

	private static void apply(Address address, AddressRequest r) {
		address.update(r.label().trim(), r.recipientName().trim(), r.phone(), r.line1().trim(),
				r.line2() != null ? r.line2().trim() : null, r.city().trim(), r.state().trim(), r.postalCode().trim(),
				r.country() != null ? r.country() : "IN");
	}

	private User load(Long userId) {
		return users.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
	}

	private Address loadAddress(Long userId, Long addressId) {
		return addresses.findByIdAndUserId(addressId, userId)
			.orElseThrow(() -> ApiException.notFound("Address", addressId));
	}

}
