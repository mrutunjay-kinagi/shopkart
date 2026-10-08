package com.mkinagi.shopkart.config;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryList;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductDetails;
import com.mkinagi.shopkart.catalog.service.ProductService;

import tools.jackson.databind.ObjectMapper;

/**
 * Redis cache regions. Each region gets a JSON serializer bound to its concrete value type, which keeps
 * cache entries human-readable in {@code redis-cli} and avoids polymorphic type information (a known
 * deserialization attack vector). Statistics are enabled so hit/miss counts appear in the metrics.
 */
@Configuration
public class CacheConfig {

	@Bean
	RedisCacheManagerBuilderCustomizer shopkartCaches(ObjectMapper objectMapper) {
		return builder -> builder.enableStatistics()
			.withCacheConfiguration(ProductService.PRODUCTS_CACHE,
					region(objectMapper, ProductDetails.class, Duration.ofMinutes(10)))
			.withCacheConfiguration(ProductService.CATEGORIES_CACHE,
					region(objectMapper, CategoryList.class, Duration.ofHours(1)));
	}

	private static <T> RedisCacheConfiguration region(ObjectMapper objectMapper, Class<T> type, Duration ttl) {
		return RedisCacheConfiguration.defaultCacheConfig()
			.prefixCacheNameWith("shopkart:")
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, type)));
	}

}
