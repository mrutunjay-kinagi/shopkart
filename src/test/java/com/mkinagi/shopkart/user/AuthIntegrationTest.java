package com.mkinagi.shopkart.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mkinagi.shopkart.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

class AuthIntegrationTest extends IntegrationTest {

	@Test
	void registerReturnsTokensAndProfileIsReadable() throws Exception {
		String email = unique("Alice") + "@Example.com";
		JsonNode auth = body(postJson("/api/v1/auth/register", null,
				Map.of("email", email, "password", PASSWORD, "fullName", "  Alice Rao ", "phone", "+919876543210"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
			.andExpect(jsonPath("$.user.role").value("CUSTOMER")));

		getJson("/api/v1/users/me", auth.get("accessToken").asString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Alice Rao"))
			.andExpect(jsonPath("$.phone").value("+919876543210"));
	}

	@Test
	void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
		Customer customer = registerCustomer();
		postJson("/api/v1/auth/register", null,
				Map.of("email", customer.email().toUpperCase(), "password", PASSWORD, "fullName", "Copy"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void weakPasswordAndBadEmailFailValidation() throws Exception {
		postJson("/api/v1/auth/register", null, Map.of("email", "not-an-email", "password", "short", "fullName", "X"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void loginWithWrongPasswordIsUnauthorized() throws Exception {
		Customer customer = registerCustomer();
		postJson("/api/v1/auth/login", null, Map.of("email", customer.email(), "password", "Wrong1234"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		postJson("/api/v1/auth/login", null, Map.of("email", "nobody@example.com", "password", "Wrong1234"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void protectedEndpointsRequireAValidToken() throws Exception {
		getJson("/api/v1/users/me", null).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		getJson("/api/v1/users/me", "not.a.jwt").andExpect(status().isUnauthorized());
		Customer customer = registerCustomer();
		getJson("/api/v1/admin/orders", customer.accessToken()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void refreshRotatesTokensAndDetectsReuse() throws Exception {
		Customer customer = registerCustomer();
		JsonNode refreshed = body(postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", customer.refreshToken()))
			.andExpect(status().isOk()));
		String rotated = refreshed.get("refreshToken").asString();
		assertThat(rotated).isNotEqualTo(customer.refreshToken());

		// Replaying the old token looks like theft: it fails and kills the whole token family.
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", customer.refreshToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", rotated)).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesAccessAndRefreshTokens() throws Exception {
		Customer customer = registerCustomer();
		postJson("/api/v1/auth/logout", customer.accessToken(), Map.of("refreshToken", customer.refreshToken()))
			.andExpect(status().isNoContent());

		getJson("/api/v1/users/me", customer.accessToken()).andExpect(status().isUnauthorized())
			.andExpect(header().exists("Content-Type"));
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", customer.refreshToken()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void passwordResetFlow() throws Exception {
		Customer customer = registerCustomer();
		postJson("/api/v1/auth/password/forgot", null, Map.of("email", customer.email()))
			.andExpect(status().isAccepted());
		// Unknown e-mails get the same response, so accounts cannot be enumerated.
		postJson("/api/v1/auth/password/forgot", null, Map.of("email", "ghost@example.com"))
			.andExpect(status().isAccepted());

		ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
		verify(notificationService, timeout(2000)).sendResetLink(eq(customer.id()), eq(customer.email()), any(),
				token.capture());

		postJson("/api/v1/auth/password/reset", null, Map.of("token", token.getValue(), "newPassword", "N3wPassword"))
			.andExpect(status().isNoContent());
		// Single use, old sessions revoked, new password works.
		postJson("/api/v1/auth/password/reset", null, Map.of("token", token.getValue(), "newPassword", "An0therOne"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", customer.refreshToken()))
			.andExpect(status().isUnauthorized());
		postJson("/api/v1/auth/login", null, Map.of("email", customer.email(), "password", PASSWORD))
			.andExpect(status().isUnauthorized());
		postJson("/api/v1/auth/login", null, Map.of("email", customer.email(), "password", "N3wPassword"))
			.andExpect(status().isOk());
	}

	@Test
	void profileAndAddressBook() throws Exception {
		Customer customer = registerCustomer();
		String token = customer.accessToken();
		patchJson("/api/v1/users/me", token, Map.of("fullName", "Renamed Customer")).andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Renamed Customer"));

		Map<String, Object> home = new HashMap<>(address());
		home.put("label", "Home");
		Map<String, Object> office = new HashMap<>(address());
		office.put("label", "Office");
		office.put("makeDefault", true);
		postJson("/api/v1/users/me/addresses", token, home).andExpect(status().isCreated())
			.andExpect(jsonPath("$.isDefault").value(true));
		postJson("/api/v1/users/me/addresses", token, office).andExpect(status().isCreated());

		getJson("/api/v1/users/me/addresses", token).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].label").value("Office"))
			.andExpect(jsonPath("$[0].isDefault").value(true))
			.andExpect(jsonPath("$[1].isDefault").value(false));
	}

}
