package com.mkinagi.shopkart.user.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.config.ShopkartProperties;

/**
 * Fixed-window limiter for login attempts per account, backed by an atomic Redis {@code INCR}. It slows
 * credential-stuffing without needing sticky sessions, since every instance shares the counter.
 */
@Component
public class LoginRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final StringRedisTemplate redis;

	private final int limit;

	public LoginRateLimiter(StringRedisTemplate redis, ShopkartProperties properties) {
		this.redis = redis;
		this.limit = properties.rateLimit().loginAttemptsPerMinute();
	}

	public void check(String email) {
		String key = "auth:login-attempts:" + email;
		Long attempts = redis.opsForValue().increment(key);
		if (attempts != null && attempts == 1) {
			redis.expire(key, WINDOW);
		}
		if (attempts != null && attempts > limit) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
					"Too many login attempts, please try again in a minute");
		}
	}

	public void reset(String email) {
		redis.delete("auth:login-attempts:" + email);
	}

}
