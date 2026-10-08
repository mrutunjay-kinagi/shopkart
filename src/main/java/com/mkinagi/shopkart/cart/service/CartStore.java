package com.mkinagi.shopkart.cart.service;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mkinagi.shopkart.config.ShopkartProperties;

/**
 * Carts live in Redis as one hash per user ({@code cart:{userId}} -> productId: quantity). Reads and
 * writes are single O(1) commands, the TTL abandons idle carts automatically, and Redis persistence
 * (AOF) keeps them across restarts. Prices are deliberately not stored: they are always read from the
 * catalogue so a cart never shows a stale price.
 */
@Component
public class CartStore {

	private final StringRedisTemplate redis;

	private final HashOperations<String, String, String> hash;

	private final Duration ttl;

	public CartStore(StringRedisTemplate redis, ShopkartProperties properties) {
		this.redis = redis;
		this.hash = redis.opsForHash();
		this.ttl = properties.cart().ttl();
	}

	/** @return product id to quantity, ordered by product id */
	public Map<Long, Integer> items(Long userId) {
		Map<Long, Integer> items = new TreeMap<>();
		hash.entries(key(userId)).forEach((k, v) -> items.put(Long.valueOf(k), Integer.valueOf(v)));
		return items;
	}

	public int quantity(Long userId, Long productId) {
		String value = hash.get(key(userId), productId.toString());
		return value == null ? 0 : Integer.parseInt(value);
	}

	public long distinctItems(Long userId) {
		return hash.size(key(userId));
	}

	public void put(Long userId, Long productId, int quantity) {
		hash.put(key(userId), productId.toString(), Integer.toString(quantity));
		redis.expire(key(userId), ttl);
	}

	public void remove(Long userId, Long productId) {
		hash.delete(key(userId), productId.toString());
	}

	public void clear(Long userId) {
		redis.delete(key(userId));
	}

	private static String key(Long userId) {
		return "cart:" + userId;
	}

}
