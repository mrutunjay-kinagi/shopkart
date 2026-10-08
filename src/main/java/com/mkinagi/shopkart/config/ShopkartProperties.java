package com.mkinagi.shopkart.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Typed application settings bound from {@code shopkart.*}.
 */
@Validated
@ConfigurationProperties(prefix = "shopkart")
public record ShopkartProperties(@Valid Jwt jwt, @Valid Payments payments, @Valid Orders orders, @Valid Cart cart,
		@Valid Web web, @Valid RateLimit rateLimit) {

	public record Jwt(@NotBlank @Size(min = 43) String secret, @DefaultValue("shopkart") String issuer,
			@DefaultValue("15m") Duration accessTokenTtl, @DefaultValue("7d") Duration refreshTokenTtl,
			@DefaultValue("15m") Duration passwordResetTtl) {
	}

	public record Payments(@NotBlank String mockpayWebhookSecret) {
	}

	public record Orders(@DefaultValue("15m") Duration paymentWindow) {
	}

	public record Cart(@DefaultValue("30d") Duration ttl, @DefaultValue("10") int maxQuantityPerItem,
			@DefaultValue("50") int maxDistinctItems) {
	}

	public record Web(@DefaultValue("http://localhost:3000") String frontendBaseUrl,
			@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {
	}

	public record RateLimit(@DefaultValue("10") int loginAttemptsPerMinute) {
	}

}
