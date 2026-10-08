package com.mkinagi.shopkart.user.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Revoked access-token ids ({@code jti}) kept in Redis only until the token would have expired anyway,
 * so the set stays small and self-cleaning.
 */
@Component
public class AccessTokenDenylist {

	private static final String PREFIX = "auth:denylist:";

	private final StringRedisTemplate redis;

	public AccessTokenDenylist(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void revoke(String jti, Instant expiresAt) {
		Duration ttl = Duration.between(Instant.now(), expiresAt);
		if (jti != null && !ttl.isNegative() && !ttl.isZero()) {
			redis.opsForValue().set(PREFIX + jti, "1", ttl);
		}
	}

	public boolean isRevoked(String jti) {
		return jti != null && Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
	}

}
