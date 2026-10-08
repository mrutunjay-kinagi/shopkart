package com.mkinagi.shopkart.catalog.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.mkinagi.shopkart.catalog.api.CatalogDtos;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductSummary;
import com.mkinagi.shopkart.common.PageResponse;

/**
 * Listing and keyword search in one dynamic SQL statement. Keyword search uses the InnoDB FULLTEXT index
 * {@code ftx_products_search} in boolean mode with prefix terms ({@code +lap* +gam*}), which ranks results
 * by relevance and avoids the full table scan a {@code LIKE '%term%'} query would need.
 */
@Repository
public class ProductSearchRepository {

	/** InnoDB's default {@code innodb_ft_min_token_size}; shorter terms are not indexed. */
	private static final int MIN_TOKEN = 3;

	private final JdbcClient jdbc;

	public ProductSearchRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public PageResponse<ProductSummary> search(ProductSearchQuery query, Collection<Long> categoryIds) {
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> where = new ArrayList<>();
		where.add("p.active = TRUE");

		String booleanQuery = toBooleanQuery(query.q());
		boolean fullText = booleanQuery != null;
		if (fullText) {
			where.add("MATCH(p.name, p.brand, p.description) AGAINST (:q IN BOOLEAN MODE)");
			params.put("q", booleanQuery);
		}
		else if (query.q() != null && !query.q().isBlank()) {
			// Only terms too short for the full-text index (e.g. "tv"): fall back to a substring match on
			// the name. It cannot use an index, but the other filters still narrow the scan.
			where.add("p.name LIKE :namePrefix");
			params.put("namePrefix", "%" + escapeLike(query.q().trim()) + "%");
		}
		if (categoryIds != null) {
			where.add("p.category_id IN (:categoryIds)");
			params.put("categoryIds", categoryIds);
		}
		if (query.brand() != null && !query.brand().isBlank()) {
			where.add("p.brand = :brand");
			params.put("brand", query.brand().trim());
		}
		if (query.minPrice() != null) {
			where.add("p.price >= :minPrice");
			params.put("minPrice", query.minPrice());
		}
		if (query.maxPrice() != null) {
			where.add("p.price <= :maxPrice");
			params.put("maxPrice", query.maxPrice());
		}
		if (query.inStockOnly()) {
			where.add("p.stock_quantity > 0");
		}
		String whereSql = String.join(" AND ", where);

		String select = """
				SELECT p.id, p.name, p.slug, p.brand, p.price, p.mrp, p.stock_quantity, c.slug AS category_slug,
				       (SELECT i.url FROM product_images i WHERE i.product_id = p.id AND i.position = 0) AS image_url
				FROM products p JOIN categories c ON c.id = p.category_id
				WHERE %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset""".formatted(whereSql, orderBy(query.sort(), fullText));
		params.put("limit", query.size());
		params.put("offset", (long) query.page() * query.size());

		List<ProductSummary> rows = jdbc.sql(select)
			.params(params)
			.query((rs, n) -> new ProductSummary(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
					rs.getString("brand"), rs.getBigDecimal("price"), rs.getBigDecimal("mrp"),
					CatalogDtos.discountPercent(rs.getBigDecimal("price"), rs.getBigDecimal("mrp")),
					rs.getInt("stock_quantity") > 0, rs.getString("category_slug"), rs.getString("image_url")))
			.list();

		long total;
		if (query.page() == 0 && rows.size() < query.size()) {
			total = rows.size();
		}
		else {
			total = jdbc.sql("SELECT COUNT(*) FROM products p WHERE " + whereSql)
				.params(params)
				.query(Long.class)
				.single();
		}
		return PageResponse.of(rows, query.page(), query.size(), total);
	}

	private static String orderBy(ProductSearchQuery.Sort sort, boolean fullText) {
		ProductSearchQuery.Sort effective = sort != null ? sort
				: fullText ? ProductSearchQuery.Sort.RELEVANCE : ProductSearchQuery.Sort.NEWEST;
		return switch (effective) {
			case RELEVANCE -> fullText ? "MATCH(p.name, p.brand, p.description) AGAINST (:q IN BOOLEAN MODE) DESC, p.id"
					: "p.id DESC";
			case PRICE_ASC -> "p.price ASC, p.id";
			case PRICE_DESC -> "p.price DESC, p.id";
			case NAME -> "p.name ASC, p.id";
			case NEWEST -> "p.id DESC";
		};
	}

	/**
	 * Turns free text into a safe boolean-mode expression: operator characters are stripped and every
	 * remaining word becomes a required prefix term. Returns {@code null} if no indexable word remains.
	 */
	static String toBooleanQuery(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		String terms = Arrays.stream(q.toLowerCase().split("\\s+"))
			.map(t -> t.replaceAll("[^\\p{L}\\p{N}]", ""))
			.filter(t -> t.length() >= MIN_TOKEN)
			.limit(8)
			.map(t -> "+" + t + "*")
			.collect(Collectors.joining(" "));
		return terms.isEmpty() ? null : terms;
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
