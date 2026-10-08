package com.mkinagi.shopkart.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mkinagi.shopkart.TestcontainersConfiguration;
import com.mkinagi.shopkart.notification.NotificationService;
import com.mkinagi.shopkart.user.domain.Role;
import com.mkinagi.shopkart.user.domain.User;
import com.mkinagi.shopkart.user.domain.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base for API-level integration tests: the full application against real MySQL, Redis and Kafka
 * containers. All subclasses share one Spring context (and therefore one set of containers); tests
 * isolate themselves with unique e-mails and SKUs instead of truncating tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

	protected static final String PASSWORD = "Passw0rd!";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected ObjectMapper json;

	/** Spy so tests can capture the password-reset token that would otherwise only be e-mailed. */
	@MockitoSpyBean
	protected NotificationService notificationService;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String adminToken;

	protected static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	// ------------------------------------------------------------------ http helpers

	protected ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		return mvc.perform(request);
	}

	protected ResultActions getJson(String url, String token) throws Exception {
		return call(get(url), token, null);
	}

	protected ResultActions postJson(String url, String token, Object body) throws Exception {
		return call(post(url), token, body);
	}

	protected ResultActions putJson(String url, String token, Object body) throws Exception {
		return call(put(url), token, body);
	}

	protected ResultActions patchJson(String url, String token, Object body) throws Exception {
		return call(patch(url), token, body);
	}

	protected ResultActions deleteJson(String url, String token) throws Exception {
		return call(delete(url), token, null);
	}

	protected JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	// ------------------------------------------------------------------ fixtures

	protected record Customer(Long id, String email, String accessToken, String refreshToken) {
	}

	protected Customer registerCustomer() throws Exception {
		String email = unique("user") + "@example.com";
		JsonNode auth = body(postJson("/api/v1/auth/register", null,
				Map.of("email", email, "password", PASSWORD, "fullName", "Test Customer", "phone", "9876543210")));
		return new Customer(auth.at("/user/id").asLong(), email, auth.get("accessToken").asString(),
				auth.get("refreshToken").asString());
	}

	protected String adminToken() throws Exception {
		if (adminToken == null) {
			String email = unique("admin") + "@shopkart.test";
			users.save(new User(email, passwordEncoder.encode(PASSWORD), "Test Admin", null, Role.ADMIN));
			adminToken = body(postJson("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD)))
				.get("accessToken")
				.asString();
		}
		return adminToken;
	}

	protected Long createCategory(String name, Long parentId) throws Exception {
		Map<String, Object> request = new HashMap<>(Map.of("name", name, "description", name + " items"));
		if (parentId != null) {
			request.put("parentId", parentId);
		}
		return body(postJson("/api/v1/admin/categories", adminToken(), request)).get("id").asLong();
	}

	protected Long createProduct(Long categoryId, String name, String price, int stock) throws Exception {
		String sku = unique("SKU").toUpperCase();
		Map<String, Object> request = Map.of("sku", sku, "name", name, "brand", "Acme", "description",
				"A dependable " + name.toLowerCase() + " for everyday use", "price", new BigDecimal(price), "mrp",
				new BigDecimal(price).add(new BigDecimal("100")), "stockQuantity", stock, "categoryId", categoryId,
				"images", List.of("https://cdn.example.com/" + sku + ".jpg"), "specifications",
				Map.of("Colour", "Black", "Warranty", "1 year"));
		return body(postJson("/api/v1/admin/products", adminToken(), request)).get("id").asLong();
	}

	protected Map<String, Object> address() {
		return Map.of("recipientName", "Test Customer", "phone", "9876543210", "line1", "12 MG Road", "city",
				"Bengaluru", "state", "Karnataka", "postalCode", "560001");
	}

}
