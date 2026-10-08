package com.mkinagi.shopkart.catalog.service;

import java.math.BigDecimal;

/**
 * Browse/search criteria. Every field is optional; {@code q} switches on full-text relevance ranking.
 */
public record ProductSearchQuery(String q, String categorySlug, String brand, BigDecimal minPrice,
		BigDecimal maxPrice, boolean inStockOnly, Sort sort, int page, int size) {

	public enum Sort {

		RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST, NAME

	}

}
