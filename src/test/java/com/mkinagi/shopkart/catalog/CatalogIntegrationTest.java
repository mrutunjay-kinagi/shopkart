package com.mkinagi.shopkart.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mkinagi.shopkart.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

class CatalogIntegrationTest extends IntegrationTest {

	@Autowired
	StringRedisTemplate redis;

	Long parent;

	Long child;

	String tag;

	@BeforeEach
	void catalogue() throws Exception {
		tag = unique("zq").replace("-", "");
		parent = createCategory(unique("Gadgets"), null);
		child = createCategory(unique("Wearables"), parent);
		createProduct(parent, "Vortex " + tag + " Laptop Stand", "1499.00", 10);
		createProduct(child, "Vortex " + tag + " Fitness Band", "2999.00", 0);
		createProduct(child, "Nimbus " + tag + " Smart Watch", "7999.00", 5);
	}

	@Test
	void keywordSearchUsesPrefixesAndRanksMatches() throws Exception {
		getJson("/api/v1/products?q=vort " + tag.substring(0, 6), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].name").value(startsWith("Vortex")));
		getJson("/api/v1/products?q=" + tag + " watch", null).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].brand").value("Acme"));
	}

	@Test
	void categoryBrowsingIncludesSubcategoriesAndFilters() throws Exception {
		String parentSlug = body(getJson("/api/v1/products?q=" + tag + " stand", null)).at("/content/0/categorySlug")
			.asString();
		getJson("/api/v1/products?category=" + parentSlug, null).andExpect(jsonPath("$.totalElements").value(3));
		getJson("/api/v1/products?category=" + parentSlug + "&inStock=true&sort=PRICE_DESC", null)
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].price").value(7999.00))
			.andExpect(jsonPath("$.content[1].price").value(1499.00));
		getJson("/api/v1/products?category=" + parentSlug + "&minPrice=2000&maxPrice=5000", null)
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].inStock").value(false));
		getJson("/api/v1/products?category=" + parentSlug + "&size=2&page=1&sort=NAME", null)
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void invalidQueriesAreRejected() throws Exception {
		getJson("/api/v1/products?minPrice=10&maxPrice=5", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PRICE_RANGE"));
		getJson("/api/v1/products?size=1000", null).andExpect(status().isBadRequest());
		getJson("/api/v1/products?category=does-not-exist", null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	@Test
	void productDetailsAreCachedInRedisAndEvictedOnUpdate() throws Exception {
		Long id = createProduct(child, "Cache " + tag + " Earbuds", "999.00", 3);
		String key = "shopkart:products::" + id;
		assertThat(redis.hasKey(key)).isFalse();

		JsonNode details = body(getJson("/api/v1/products/" + id, null).andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "max-age=60, public"))
			.andExpect(jsonPath("$.specifications.Colour").value("Black"))
			.andExpect(jsonPath("$.images.length()").value(1))
			.andExpect(jsonPath("$.discountPercent").value(9)));
		assertThat(redis.hasKey(key)).isTrue();
		assertThat(redis.opsForValue().get(key)).contains("\"sku\"");

		getJson("/api/v1/products/slug/" + details.get("slug").asString(), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id));

		patchJson("/api/v1/admin/products/" + id + "/stock", adminToken(), Map.of("delta", 7))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stockQuantity").value(10));
		assertThat(redis.hasKey(key)).isFalse();
		getJson("/api/v1/products/" + id, null).andExpect(jsonPath("$.stockQuantity").value(10));
	}

	@Test
	void deactivatedProductsDisappear() throws Exception {
		Long id = createProduct(child, "Retired " + tag + " Gizmo", "199.00", 3);
		deleteJson("/api/v1/admin/products/" + id, adminToken()).andExpect(status().isNoContent());
		getJson("/api/v1/products/" + id, null).andExpect(status().isNotFound());
		getJson("/api/v1/products?q=retired " + tag, null).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void adminWritesAreProtectedAndValidated() throws Exception {
		Customer customer = registerCustomer();
		postJson("/api/v1/admin/categories", customer.accessToken(), Map.of("name", "Hack"))
			.andExpect(status().isForbidden());
		postJson("/api/v1/admin/products", adminToken(),
				Map.of("sku", "bad sku", "name", "", "description", "x", "price", -1, "mrp", 10, "stockQuantity", 1,
						"categoryId", child))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.sku").exists())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.price").exists());
		postJson("/api/v1/admin/products", adminToken(),
				Map.of("sku", unique("SKU").toUpperCase(), "name", "Pricey", "description", "x", "price", 200, "mrp",
						100, "stockQuantity", 1, "categoryId", child))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PRICE_ABOVE_MRP"));
	}

	@Test
	void categoriesAreListedPublicly() throws Exception {
		getJson("/api/v1/categories", null).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == " + child + ")].parentId").value(parent.intValue()));
	}

}
