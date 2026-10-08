package com.mkinagi.shopkart.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.user.api.AuthDtos.AuthResponse;
import com.mkinagi.shopkart.user.api.AuthDtos.ForgotPasswordRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.LoginRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.LogoutRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.RefreshRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.RegisterRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.ResetPasswordRequest;
import com.mkinagi.shopkart.user.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, session refresh and password reset")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a customer account and sign in")
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	@Operation(summary = "Exchange credentials for an access and refresh token")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate the refresh token and obtain a new access token")
	public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke the current access token and its refresh-token family")
	public void logout(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) LogoutRequest request) {
		authService.logout(jwt, request != null ? request.refreshToken() : null);
	}

	@PostMapping("/password/forgot")
	@Operation(summary = "E-mail a single-use password reset link")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.requestPasswordReset(request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Set a new password using the token from the reset e-mail")
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request.token(), request.newPassword());
	}

}
