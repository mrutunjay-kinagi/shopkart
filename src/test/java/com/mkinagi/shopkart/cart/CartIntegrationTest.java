package com.mkinagi.shopkart.cart;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mkinagi.shopkart.support.IntegrationTest;

class CartIntegrationTest extends IntegrationTest {

	Long category;

	String token;

	@BeforeEach
	void setUp() throws Exception {
		category = createCategory(unique("CartCat"), null);
		token = registerCustomer().accessToken();
	}

	@Test
	void addAccumulatesAndTotalsAreComputedServerSide() throws Exception {
		Long mug = createProduct(category, "Ceramic Mug", "199.00", 20);
		Long lamp = createProduct(category, "Desk Lamp", "899.00", 5);

		postJson("/api/v1/cart/items", token, Map.of("productId", mug, "quantity", 1)).andExpect(status().isOk());
		postJson("/api/v1/cart/items", token, Map.of("productId", mug, "quantity", 1))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.subtotal").value(398.00))
			.andExpect(jsonPath("$.shippingFee").value(49.00))
			.andExpect(jsonPath("$.total").value(447.00));

		postJson("/api/v1/cart/items", token, Map.of("productId", lamp, "quantity", 1))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.totalQuantity").value(3))
			.andExpect(jsonPath("$.subtotal").value(1297.00))
			.andExpect(jsonPath("$.savings").value(300.00))
			.andExpect(jsonPath("$.shippingFee").value(0.00))
			.andExpect(jsonPath("$.checkoutReady").value(true));

		putJson("/api/v1/cart/items/" + mug, token, Map.of("quantity", 5))
			.andExpect(jsonPath("$.items[?(@.productId == " + mug + ")].quantity").value(5));
		deleteJson("/api/v1/cart/items/" + lamp, token).andExpect(jsonPath("$.items.length()").value(1));
		deleteJson("/api/v1/cart", token).andExpect(status().isNoContent());
		getJson("/api/v1/cart", token).andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.checkoutReady").value(false));
	}

	@Test
	void stockAndQuantityLimitsAreEnforced() throws Exception {
		Long scarce = createProduct(category, "Limited Print", "499.00", 2);
		postJson("/api/v1/cart/items", token, Map.of("productId", scarce, "quantity", 3))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

		Long plenty = createProduct(category, "Pencil", "10.00", 500);
		postJson("/api/v1/cart/items", token, Map.of("productId", plenty, "quantity", 11))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("QUANTITY_LIMIT"));
		postJson("/api/v1/cart/items", token, Map.of("productId", 999_999_999, "quantity", 1))
			.andExpect(status().isNotFound());
		postJson("/api/v1/cart/items", token, Map.of("productId", plenty, "quantity", 0))
			.andExpect(status().isBadRequest());
	}

	@Test
	void cartFlagsLinesThatWentOutOfStock() throws Exception {
		Long item = createProduct(category, "Flash Sale Kettle", "699.00", 3);
		postJson("/api/v1/cart/items", token, Map.of("productId", item, "quantity", 2)).andExpect(status().isOk());
		patchJson("/api/v1/admin/products/" + item + "/stock", adminToken(), Map.of("delta", -2))
			.andExpect(status().isOk());

		getJson("/api/v1/cart", token).andExpect(jsonPath("$.items[0].available").value(false))
			.andExpect(jsonPath("$.items[0].issue").value("ONLY_1_LEFT"))
			.andExpect(jsonPath("$.subtotal").value(0.00))
			.andExpect(jsonPath("$.checkoutReady").value(false));
	}

	@Test
	void cartRequiresAuthentication() throws Exception {
		getJson("/api/v1/cart", null).andExpect(status().isUnauthorized());
	}

}
