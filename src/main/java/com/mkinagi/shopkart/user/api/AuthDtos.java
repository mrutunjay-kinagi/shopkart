package com.mkinagi.shopkart.user.api;

import java.time.Instant;

import com.mkinagi.shopkart.user.domain.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies of the authentication and profile endpoints.
 */
public final class AuthDtos {

	static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

	static final String PASSWORD_MESSAGE = "must be 8-72 characters and contain a letter and a digit";

	static final String PHONE_RULE = "^\\+?[0-9]{10,15}$";

	private AuthDtos() {
	}

	public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
			@NotBlank @Size(max = 120) String fullName,
			@Pattern(regexp = PHONE_RULE, message = "must be a valid phone number") String phone) {
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record LogoutRequest(String refreshToken) {
	}

	public record ForgotPasswordRequest(@NotBlank @Email String email) {
	}

	public record ResetPasswordRequest(@NotBlank String token,
			@NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
	}

	public record UpdateProfileRequest(@Size(min = 1, max = 120) String fullName,
			@Pattern(regexp = PHONE_RULE, message = "must be a valid phone number") String phone) {
	}

	public record UserResponse(Long id, String email, String fullName, String phone, String role, Instant createdAt) {

		public static UserResponse from(User user) {
			return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
					user.getRole().name(), user.getCreatedAt());
		}

	}

	public record AuthResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
			UserResponse user) {
	}

}
