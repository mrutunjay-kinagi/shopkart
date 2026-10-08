package com.mkinagi.shopkart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.mkinagi.shopkart.notification.Notification;
import com.mkinagi.shopkart.notification.NotificationRepository;
import com.mkinagi.shopkart.order.service.OrderService;
import com.mkinagi.shopkart.payment.domain.Payment;
import com.mkinagi.shopkart.payment.domain.PaymentRepository;
import com.mkinagi.shopkart.payment.gateway.MockPayGateway;
import com.mkinagi.shopkart.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * End-to-end order flows across the cart, order, payment and notification modules, including the
 * asynchronous Kafka hops between them.
 */
class OrderLifecycleIntegrationTest extends IntegrationTest {

	private static final Duration ASYNC = Duration.ofSeconds(15);

	@Autowired
	MockPayGateway mockPay;

	@Autowired
	OrderService orderService;

	@Autowired
	NotificationRepository notifications;

	@Autowired
	PaymentRepository payments;

	Long category;

	Customer customer;

	@BeforeEach
	void setUp() throws Exception {
		category = createCategory(unique("Orders"), null);
		customer = registerCustomer();
	}

	@Test
	void cardOrderFromCheckoutToDelivery() throws Exception {
		Long phone = createProduct(category, "Pocket Phone", "15000.00", 10);
		Long cover = createProduct(category, "Phone Cover", "250.00", 10);
		addToCart(phone, 1);
		addToCart(cover, 2);

		JsonNode order = body(checkout("CARD", null).andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.nextAction").value("PAY"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.subtotal").value(15500.00))
			.andExpect(jsonPath("$.shippingFee").value(0.00))
			.andExpect(jsonPath("$.totalAmount").value(15500.00))
			.andExpect(jsonPath("$.orderNumber").value(matchesPattern("SK-\\d{6}-[A-Z2-9]{6}"))));
		long orderId = order.get("id").asLong();

		// Stock is reserved at checkout and the cart is emptied.
		getJson("/api/v1/products/" + phone, null).andExpect(jsonPath("$.stockQuantity").value(9));
		getJson("/api/v1/cart", customer.accessToken()).andExpect(jsonPath("$.items.length()").value(0));

		JsonNode payment = body(pay(orderId, Map.of("cardToken", "tok_visa_4242")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.instrument").value("VISA **** 4242"))
			.andExpect(jsonPath("$.receiptNumber").exists()));

		awaitOrderStatus(orderId, "CONFIRMED");
		getJson("/api/v1/payments/" + payment.get("id").asLong() + "/receipt", customer.accessToken())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderNumber").value(order.get("orderNumber").asString()))
			.andExpect(jsonPath("$.totalPaid").value(15500.00))
			.andExpect(jsonPath("$.lines.length()").value(2));

		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(), Map.of("status", "SHIPPED"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TRACKING_REQUIRED"));
		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(),
				Map.of("status", "SHIPPED", "carrier", "BlueDart", "trackingNumber", "BD123456789IN"))
			.andExpect(status().isOk());
		getJson("/api/v1/orders/" + orderId + "/tracking", customer.accessToken()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SHIPPED"))
			.andExpect(jsonPath("$.carrier").value("BlueDart"))
			.andExpect(jsonPath("$.trackingNumber").value("BD123456789IN"))
			.andExpect(jsonPath("$.estimatedDelivery").exists())
			.andExpect(jsonPath("$.timeline[*].status").value(contains("PENDING_PAYMENT",
					"CONFIRMED", "SHIPPED")));

		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(), Map.of("status", "OUT_FOR_DELIVERY"))
			.andExpect(status().isOk());
		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(), Map.of("status", "DELIVERED"))
			.andExpect(jsonPath("$.status").value("DELIVERED"))
			.andExpect(jsonPath("$.nextAction").value("NONE"));
		// The state machine does not allow going backwards.
		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(),
				Map.of("status", "SHIPPED", "carrier", "X", "trackingNumber", "Y"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));

		getJson("/api/v1/orders", customer.accessToken()).andExpect(jsonPath("$.content[0].id").value(orderId))
			.andExpect(jsonPath("$.content[0].itemCount").value(3));

		await().atMost(ASYNC)
			.untilAsserted(() -> assertThat(notifications.findByUserIdOrderByIdAsc(customer.id()))
				.extracting(Notification::getTemplate)
				.contains("WELCOME", "ORDER_PLACED", "PAYMENT_RECEIPT", "ORDER_CONFIRMED", "ORDER_SHIPPED",
						"ORDER_DELIVERED"));
	}

	@Test
	void checkoutIsIdempotentPerKey() throws Exception {
		Long item = createProduct(category, "Notebook", "120.00", 10);
		addToCart(item, 3);
		String key = unique("checkout");
		JsonNode first = body(checkout("CARD", key).andExpect(status().isCreated()));
		// The client retries after a timeout: same order back, nothing charged or reserved twice.
		checkout("CARD", key).andExpect(status().isOk())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.id").value(first.get("id").asLong()));
		getJson("/api/v1/products/" + item, null).andExpect(jsonPath("$.stockQuantity").value(7));
	}

	@Test
	void declinedCardCanBeRetriedButNotPaidTwice() throws Exception {
		long orderId = placeOrder("CARD", "2500.00");
		pay(orderId, Map.of("cardToken", "tok_fail_insufficient_funds")).andExpect(status().isPaymentRequired())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureReason").value("Card declined by issuer"));
		getJson("/api/v1/orders/" + orderId, customer.accessToken())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

		pay(orderId, Map.of("cardToken", "tok_mastercard_5555")).andExpect(status().isCreated());
		awaitOrderStatus(orderId, "CONFIRMED");
		pay(orderId, Map.of("cardToken", "tok_visa_4242")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
		getJson("/api/v1/orders/" + orderId + "/payments", customer.accessToken())
			.andExpect(jsonPath("$[*].status").value(contains("FAILED", "SUCCEEDED")));
	}

	@Test
	void upiIsConfirmedBySignedWebhook() throws Exception {
		long orderId = placeOrder("UPI", "799.00");
		JsonNode payment = body(pay(orderId, Map.of("upiVpa", "asha.k@okhdfc")).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.instrument").value("UPI as***@okhdfc")));
		pay(orderId, Map.of("upiVpa", "asha.k@okhdfc")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

		String reference = gatewayReference(orderId);
		byte[] webhook = json.writeValueAsBytes(Map.of("gatewayReference", reference, "status", "SUCCEEDED"));
		mvc.perform(post("/api/v1/payments/webhooks/mockpay").contentType(MediaType.APPLICATION_JSON)
			.header("X-MockPay-Signature", "deadbeef")
			.content(webhook)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/payments/webhooks/mockpay").contentType(MediaType.APPLICATION_JSON)
			.header("X-MockPay-Signature", mockPay.sign(webhook))
			.content(webhook)).andExpect(status().isNoContent());
		// Gateways retry webhooks; a duplicate is acknowledged and ignored.
		mvc.perform(post("/api/v1/payments/webhooks/mockpay").contentType(MediaType.APPLICATION_JSON)
			.header("X-MockPay-Signature", mockPay.sign(webhook))
			.content(webhook)).andExpect(status().isNoContent());

		awaitOrderStatus(orderId, "CONFIRMED");
		getJson("/api/v1/payments/" + payment.get("id").asLong() + "/receipt", customer.accessToken())
			.andExpect(status().isOk());
	}

	@Test
	void cashOnDeliveryIsConfirmedImmediatelyAndSettledOnDelivery() throws Exception {
		long orderId = placeOrder("COD", "300.00");
		getJson("/api/v1/orders/" + orderId, customer.accessToken()).andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.shippingFee").value(49.00))
			.andExpect(jsonPath("$.totalAmount").value(349.00));
		pay(orderId, Map.of("cardToken", "tok_visa_4242")).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("COD_ORDER"));
		await().atMost(ASYNC).untilAsserted(() -> getJson("/api/v1/orders/" + orderId + "/payments",
				customer.accessToken()).andExpect(jsonPath("$[0].status").value("PENDING")));

		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(),
				Map.of("status", "SHIPPED", "carrier", "Delhivery", "trackingNumber", "DL998877"))
			.andExpect(status().isOk());
		patchJson("/api/v1/admin/orders/" + orderId + "/status", adminToken(), Map.of("status", "DELIVERED"))
			.andExpect(status().isOk());
		await().atMost(ASYNC)
			.untilAsserted(() -> getJson("/api/v1/orders/" + orderId + "/payments", customer.accessToken())
				.andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$[0].receiptNumber").exists()));
	}

	@Test
	void cancellingAPaidOrderRestocksAndRefunds() throws Exception {
		Long item = createProduct(category, "Bluetooth Speaker", "1999.00", 4);
		addToCart(item, 2);
		long orderId = body(checkout("CARD", null)).get("id").asLong();
		pay(orderId, Map.of("cardToken", "tok_rupay_6070")).andExpect(status().isCreated());
		awaitOrderStatus(orderId, "CONFIRMED");
		getJson("/api/v1/products/" + item, null).andExpect(jsonPath("$.stockQuantity").value(2));

		postJson("/api/v1/orders/" + orderId + "/cancel", customer.accessToken(), Map.of("reason", "Ordered by mistake"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		getJson("/api/v1/products/" + item, null).andExpect(jsonPath("$.stockQuantity").value(4));
		await().atMost(ASYNC)
			.untilAsserted(() -> getJson("/api/v1/orders/" + orderId + "/payments", customer.accessToken())
				.andExpect(jsonPath("$[0].status").value("REFUNDED")));
		postJson("/api/v1/orders/" + orderId + "/cancel", customer.accessToken(), null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
	}

	@Test
	void unpaidOrdersExpireAndReleaseStock() throws Exception {
		Long item = createProduct(category, "Limited Sneakers", "4999.00", 1);
		addToCart(item, 1);
		long orderId = body(checkout("CARD", null)).get("id").asLong();
		getJson("/api/v1/products/" + item, null).andExpect(jsonPath("$.stockQuantity").value(0));

		assertThat(orderService.expire(orderId, Instant.now())).isFalse();
		assertThat(orderService.expire(orderId, Instant.now().plus(Duration.ofMinutes(16)))).isTrue();

		getJson("/api/v1/orders/" + orderId, customer.accessToken()).andExpect(jsonPath("$.status").value("CANCELLED"));
		getJson("/api/v1/products/" + item, null).andExpect(jsonPath("$.stockQuantity").value(1));
		pay(orderId, Map.of("cardToken", "tok_visa_4242")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
	}

	@Test
	void checkoutValidation() throws Exception {
		checkout("CARD", null).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("CART_EMPTY"));
		postJson("/api/v1/orders/checkout", customer.accessToken(), Map.of("paymentMethod", "CARD"))
			.andExpect(status().isBadRequest());
		postJson("/api/v1/orders/checkout", customer.accessToken(),
				Map.of("paymentMethod", "BITCOIN", "shippingAddress", address()))
			.andExpect(status().isBadRequest());

		Long item = createProduct(category, "Popular Gadget", "999.00", 2);
		addToCart(item, 2);
		patchJson("/api/v1/admin/products/" + item + "/stock", adminToken(), Map.of("delta", -1));
		checkout("CARD", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
		// Nothing was reserved and the cart is kept so the customer can adjust it.
		getJson("/api/v1/products/" + item, null).andExpect(jsonPath("$.stockQuantity").value(1));
		getJson("/api/v1/cart", customer.accessToken()).andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void savedAddressCanBeUsedAndOrdersArePrivate() throws Exception {
		Map<String, Object> saved = new HashMap<>(address());
		saved.put("label", "Home");
		long addressId = body(postJson("/api/v1/users/me/addresses", customer.accessToken(), saved)).get("id").asLong();
		Long item = createProduct(category, "Water Bottle", "549.00", 5);
		addToCart(item, 1);
		long orderId = body(postJson("/api/v1/orders/checkout", customer.accessToken(),
				Map.of("addressId", addressId, "paymentMethod", "NETBANKING"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.shippingAddress.city").value("Bengaluru"))).get("id").asLong();
		pay(orderId, Map.of("bankCode", "HDFC")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.instrument").value("Netbanking HDFC"));

		Customer stranger = registerCustomer();
		getJson("/api/v1/orders/" + orderId, stranger.accessToken()).andExpect(status().isNotFound());
		getJson("/api/v1/orders/" + orderId + "/tracking", stranger.accessToken()).andExpect(status().isNotFound());
		postJson("/api/v1/orders/" + orderId + "/payments", stranger.accessToken(), Map.of("bankCode", "HDFC"))
			.andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ helpers

	private void addToCart(Long productId, int quantity) throws Exception {
		postJson("/api/v1/cart/items", customer.accessToken(), Map.of("productId", productId, "quantity", quantity))
			.andExpect(status().isOk());
	}

	private ResultActions checkout(String method, String idempotencyKey)
			throws Exception {
		var request = post("/api/v1/orders/checkout");
		if (idempotencyKey != null) {
			request.header("Idempotency-Key", idempotencyKey);
		}
		return call(request, customer.accessToken(), Map.of("shippingAddress", address(), "paymentMethod", method));
	}

	private long placeOrder(String method, String price) throws Exception {
		addToCart(createProduct(category, unique("Item"), price, 5), 1);
		return body(checkout(method, null).andExpect(status().isCreated())).get("id").asLong();
	}

	private ResultActions pay(long orderId, Map<String, Object> instrument)
			throws Exception {
		return postJson("/api/v1/orders/" + orderId + "/payments", customer.accessToken(), instrument);
	}

	private void awaitOrderStatus(long orderId, String expected) {
		await().atMost(ASYNC)
			.untilAsserted(() -> getJson("/api/v1/orders/" + orderId, customer.accessToken())
				.andExpect(jsonPath("$.status").value(expected)));
	}

	private String gatewayReference(long orderId) {
		List<Payment> attempts = payments.findByOrderIdOrderByIdAsc(orderId);
		return attempts.get(attempts.size() - 1).getGatewayReference();
	}

}
