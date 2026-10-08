package com.mkinagi.shopkart.user.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.common.web.CurrentUser;
import com.mkinagi.shopkart.user.api.AddressDtos.AddressRequest;
import com.mkinagi.shopkart.user.api.AddressDtos.AddressResponse;
import com.mkinagi.shopkart.user.api.AuthDtos.UpdateProfileRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.UserResponse;
import com.mkinagi.shopkart.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Profile", description = "The signed-in user's profile and address book")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	@Operation(summary = "View profile")
	public UserResponse profile(@AuthenticationPrincipal Jwt jwt) {
		return userService.profile(CurrentUser.id(jwt));
	}

	@PatchMapping
	@Operation(summary = "Update name or phone")
	public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(CurrentUser.id(jwt), request);
	}

	@GetMapping("/addresses")
	@Operation(summary = "List saved addresses, default first")
	public List<AddressResponse> addresses(@AuthenticationPrincipal Jwt jwt) {
		return userService.addresses(CurrentUser.id(jwt));
	}

	@PostMapping("/addresses")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Save a new address")
	public AddressResponse addAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
		return userService.addAddress(CurrentUser.id(jwt), request);
	}

	@PutMapping("/addresses/{addressId}")
	@Operation(summary = "Replace a saved address")
	public AddressResponse updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId,
			@Valid @RequestBody AddressRequest request) {
		return userService.updateAddress(CurrentUser.id(jwt), addressId, request);
	}

	@DeleteMapping("/addresses/{addressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a saved address")
	public void deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId) {
		userService.deleteAddress(CurrentUser.id(jwt), addressId);
	}

}
