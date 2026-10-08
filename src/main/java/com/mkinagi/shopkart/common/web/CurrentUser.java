package com.mkinagi.shopkart.common.web;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Helpers for reading the authenticated principal from a validated access token.
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

}
