package com.mkinagi.shopkart.benchmark;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mkinagi.shopkart.cart.service.CartService;
import com.mkinagi.shopkart.catalog.service.ProductSearchQuery;
import com.mkinagi.shopkart.catalog.service.ProductService;
import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.order.api.OrderDtos.AddressInput;
import com.mkinagi.shopkart.order.api.OrderDtos.CheckoutRequest;
import com.mkinagi.shopkart.order.service.CheckoutService;
import com.mkinagi.shopkart.support.IntegrationTest;

/**
 * Before/after measurements quoted in the project report. Excluded from the normal build; run with
 * {@code ./mvnw test -Pbenchmark}. Results are written to {@code target/benchmark/}.
 */
@Tag("benchmark")
class PerformanceBenchmarkTest extends IntegrationTest {

	private static final int PRODUCTS = 100_000;

	private static final String[] BRANDS = { "Acme", "Zenith", "Orion", "Nova", "Apex", "Lumen", "Vertex", "Quanta",
			"Helix", "Strata", "Boreal", "Cobalt" };

	private static final String[] ADJECTIVES = { "Wireless", "Portable", "Premium", "Classic", "Smart", "Compact",
			"Ergonomic", "Waterproof", "Stainless", "Organic", "Vintage", "Ultra", "Foldable", "Rechargeable" };

	private static final String[] NOUNS = { "Headphones", "Speaker", "Laptop", "Backpack", "Kettle", "Shirt", "Sneakers",
			"Watch", "Keyboard", "Mouse", "Blender", "Jacket", "Lamp", "Bottle", "Charger", "Monitor", "Camera",
			"Router", "Wallet", "Sunglasses" };

	private static final String[] FILLER = { "durable", "lightweight", "everyday", "design", "quality", "comfortable",
			"warranty", "steel", "cotton", "bluetooth", "gaming", "travel", "kitchen", "office", "outdoor", "battery",
			"fast", "charging", "premium", "finish", "graphite", "aluminium", "leather", "eco", "friendly" };

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ProductService productService;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	CartService cartService;

	@Autowired
	CheckoutService checkoutService;

	private final Map<String, Object> results = new LinkedHashMap<>();

	@Test
	void run() throws Exception {
		Long categoryId = seed();
		results.put("environment", Map.of("products", PRODUCTS, "cpu", System.getProperty("os.arch"), "jvm",
				Runtime.version().toString(), "measuredAt", Instant.now().toString()));
		benchmarkSearch();
		benchmarkCategoryIndex(categoryId);
		benchmarkProductCache();
		benchmarkCheckout(categoryId);
		write();
	}

	// ------------------------------------------------------------------ search: LIKE vs FULLTEXT

	private void benchmarkSearch() {
		String[][] queries = { { "wireless" }, { "gaming", "laptop" }, { "stainless", "kettle" },
				{ "leather", "wallet" }, { "zenith", "camera" } };
		List<Map<String, Object>> rows = new ArrayList<>();
		for (String[] terms : queries) {
			String text = String.join(" ", terms);
			Stats like = measure(30, () -> likeSearch(terms));
			Stats fullText = measure(30,
					() -> productService.search(new ProductSearchQuery(text, null, null, null, null, false,
							ProductSearchQuery.Sort.RELEVANCE, 0, 20)).totalElements());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("query", text);
			row.put("matches", fullText.lastResult);
			row.put("likeMatches", like.lastResult);
			row.put("like", like.summary());
			row.put("fulltext", fullText.summary());
			row.put("speedup", round(like.p50 / fullText.p50));
			row.put("likePlan", explain("SELECT id FROM products p WHERE " + likeWhere(terms)));
			row.put("fulltextPlan", explain(
					"SELECT id FROM products p WHERE MATCH(p.name,p.brand,p.description) AGAINST ('" + booleanQuery(terms)
							+ "' IN BOOLEAN MODE)"));
			rows.add(row);
		}
		results.put("search", rows);
	}

	private long likeSearch(String[] terms) {
		String where = likeWhere(terms);
		jdbc.queryForList("SELECT p.id, p.name, p.price FROM products p WHERE " + where + " ORDER BY p.id DESC LIMIT 20");
		return jdbc.queryForObject("SELECT COUNT(*) FROM products p WHERE " + where, Long.class);
	}

	private static String likeWhere(String[] terms) {
		return "p.active = TRUE AND " + String.join(" AND ", Arrays.stream(terms)
			.map(t -> "(p.name LIKE '%" + t + "%' OR p.brand LIKE '%" + t + "%' OR p.description LIKE '%" + t + "%')")
			.toList());
	}

	private static String booleanQuery(String[] terms) {
		return String.join(" ", Arrays.stream(terms).map(t -> "+" + t + "*").toList());
	}

	private Map<String, Object> explain(String sql) {
		Map<String, Object> plan = jdbc.queryForList("EXPLAIN " + sql).get(0);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("type", plan.get("type"));
		out.put("key", plan.get("key"));
		out.put("rows", plan.get("rows"));
		return out;
	}

	// ------------------------------------------------------------------ browse: composite index

	private void benchmarkCategoryIndex(Long categoryId) {
		String sql = "SELECT id, name, price FROM products %s WHERE category_id = ? AND active = TRUE ORDER BY price LIMIT 20";
		String withIndex = sql.formatted("FORCE INDEX (idx_products_category_active_price)");
		String withoutIndex = sql.formatted("IGNORE INDEX (idx_products_category_active_price)");
		Stats indexed = measure(50, () -> jdbc.queryForList(withIndex, categoryId).size());
		Stats scan = measure(50, () -> jdbc.queryForList(withoutIndex, categoryId).size());
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("withoutIndex", scan.summary());
		row.put("withIndex", indexed.summary());
		row.put("speedup", round(scan.p50 / indexed.p50));
		row.put("planWithout", explain(withoutIndex.replace("?", categoryId.toString())));
		row.put("planWith", explain(withIndex.replace("?", categoryId.toString())));
		results.put("categoryBrowse", row);
	}

	// ------------------------------------------------------------------ product page: Redis cache

	private void benchmarkProductCache() throws Exception {
		List<Long> ids = jdbc.queryForList("SELECT id FROM products ORDER BY RAND(42) LIMIT 500", Long.class);
		Cache cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
		Random random = new Random(7);

		Stats dbService = measure(2000, () -> productService.getUncached(ids.get(random.nextInt(ids.size()))).id());
		ids.forEach(productService::get);
		Stats redisService = measure(2000, () -> productService.get(ids.get(random.nextInt(ids.size()))).id());

		Stats httpMiss = measureHttp(1000, ids, random, id -> cache.evict(id));
		ids.forEach(productService::get);
		Stats httpHit = measureHttp(1000, ids, random, id -> {
		});

		Map<String, Object> row = new LinkedHashMap<>();
		row.put("serviceMySql", dbService.summary());
		row.put("serviceRedis", redisService.summary());
		row.put("serviceSpeedup", round(dbService.p50 / redisService.p50));
		row.put("httpCacheMiss", httpMiss.summary());
		row.put("httpCacheHit", httpHit.summary());
		row.put("httpSpeedup", round(httpMiss.p50 / httpHit.p50));
		row.put("httpThroughputMissRps", round(1000.0 / httpMiss.mean));
		row.put("httpThroughputHitRps", round(1000.0 / httpHit.mean));
		results.put("productCache", row);
	}

	private Stats measureHttp(int iterations, List<Long> ids, Random random, java.util.function.Consumer<Long> before)
			throws Exception {
		List<Double> samples = new ArrayList<>();
		for (int i = 0; i < iterations + 50; i++) {
			Long id = ids.get(random.nextInt(ids.size()));
			before.accept(id);
			long start = System.nanoTime();
			mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk());
			if (i >= 50) {
				samples.add((System.nanoTime() - start) / 1e6);
			}
		}
		return Stats.of(samples, null);
	}

	// ------------------------------------------------------------------ checkout latency

	private void benchmarkCheckout(Long categoryId) throws Exception {
		Long a = jdbc.queryForObject("SELECT id FROM products WHERE category_id = ? LIMIT 1", Long.class, categoryId);
		Long b = jdbc.queryForObject("SELECT id FROM products WHERE category_id = ? LIMIT 1 OFFSET 1", Long.class,
				categoryId);
		jdbc.update("UPDATE products SET stock_quantity = 100000 WHERE id IN (?, ?)", a, b);
		cacheManager.getCache(ProductService.PRODUCTS_CACHE).clear();
		CheckoutRequest request = new CheckoutRequest(null,
				new AddressInput("Bench", "9876543210", "1 Bench Road", null, "Pune", "MH", "411001", "IN"),
				PaymentMethod.UPI);
		List<Long> users = new ArrayList<>();
		for (int i = 0; i < 220; i++) {
			users.add(registerCustomer().id());
		}
		List<Double> samples = new ArrayList<>();
		for (int i = 0; i < users.size(); i++) {
			Long user = users.get(i);
			cartService.addItem(user, a, 1);
			cartService.addItem(user, b, 2);
			long start = System.nanoTime();
			checkoutService.checkout(user, request, null);
			if (i >= 20) {
				samples.add((System.nanoTime() - start) / 1e6);
			}
		}
		results.put("checkout", Stats.of(samples, null).summary());
	}

	// ------------------------------------------------------------------ data + stats

	private Long seed() {
		jdbc.update("INSERT INTO categories (name, slug, description, created_at, updated_at) VALUES "
				+ "('Bench Root', 'bench-root', 'benchmark', NOW(6), NOW(6))");
		Long root = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'bench-root'", Long.class);
		List<Long> categories = new ArrayList<>();
		for (int c = 0; c < 20; c++) {
			jdbc.update("INSERT INTO categories (name, slug, parent_id, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
					"Bench " + c, "bench-" + c, root);
			categories.add(jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, "bench-" + c));
		}
		Random random = new Random(2026);
		int batch = 5_000;
		for (int start = 0; start < PRODUCTS; start += batch) {
			List<Object[]> rows = new ArrayList<>(batch);
			for (int i = start; i < start + batch; i++) {
				String brand = BRANDS[random.nextInt(BRANDS.length)];
				String name = brand + " " + ADJECTIVES[random.nextInt(ADJECTIVES.length)] + " "
						+ NOUNS[random.nextInt(NOUNS.length)] + " " + (100 + random.nextInt(900));
				StringBuilder description = new StringBuilder();
				for (int w = 0; w < 30; w++) {
					description.append(FILLER[random.nextInt(FILLER.length)]).append(' ');
				}
				BigDecimal price = BigDecimal.valueOf(199 + random.nextInt(50_000));
				rows.add(new Object[] { "BENCH-" + i, name, "bench-" + i, brand, description.toString().trim(), price,
						price.add(BigDecimal.valueOf(random.nextInt(5_000))), random.nextInt(200),
						categories.get(random.nextInt(categories.size())) });
			}
			jdbc.batchUpdate("""
					INSERT INTO products (sku, name, slug, brand, description, price, mrp, stock_quantity, category_id,
					                      active, version, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, 0, NOW(6), NOW(6))""", rows);
		}
		jdbc.update("""
				INSERT INTO product_images (product_id, position, url)
				SELECT id, 0, CONCAT('https://cdn.example.com/', sku, '.jpg') FROM products WHERE sku LIKE 'BENCH-%'""");
		jdbc.update("""
				INSERT INTO product_specifications (product_id, spec_key, spec_value)
				SELECT id, 'Warranty', '1 year' FROM products WHERE sku LIKE 'BENCH-%'""");
		jdbc.execute("ANALYZE TABLE products");
		return categories.get(0);
	}

	private Stats measure(int iterations, Supplier<Object> action) {
		for (int i = 0; i < Math.max(5, iterations / 10); i++) {
			action.get();
		}
		List<Double> samples = new ArrayList<>(iterations);
		Object last = null;
		for (int i = 0; i < iterations; i++) {
			long start = System.nanoTime();
			last = action.get();
			samples.add((System.nanoTime() - start) / 1e6);
		}
		return Stats.of(samples, last);
	}

	record Stats(double mean, double p50, double p95, double p99, int samples, Object lastResult) {

		static Stats of(List<Double> samples, Object lastResult) {
			double[] sorted = samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
			double mean = Arrays.stream(sorted).average().orElse(0);
			return new Stats(mean, pct(sorted, 50), pct(sorted, 95), pct(sorted, 99), sorted.length, lastResult);
		}

		static double pct(double[] sorted, int p) {
			int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
			return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
		}

		Map<String, Object> summary() {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("meanMs", round(mean));
			s.put("p50Ms", round(p50));
			s.put("p95Ms", round(p95));
			s.put("p99Ms", round(p99));
			s.put("samples", samples);
			return s;
		}

	}

	private static double round(double value) {
		return Math.round(value * 1000) / 1000.0;
	}

	private void write() throws IOException {
		Path dir = Path.of("target", "benchmark");
		Files.createDirectories(dir);
		String output = json.writerWithDefaultPrettyPrinter().writeValueAsString(results);
		Files.writeString(dir.resolve("results.json"), output);
		System.out.println("BENCHMARK RESULTS\n" + output);
	}

}
