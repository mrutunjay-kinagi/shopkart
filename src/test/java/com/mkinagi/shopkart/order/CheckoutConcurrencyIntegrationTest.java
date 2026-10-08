package com.mkinagi.shopkart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mkinagi.shopkart.cart.service.CartService;
import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.order.api.OrderDtos.AddressInput;
import com.mkinagi.shopkart.order.api.OrderDtos.CheckoutRequest;
import com.mkinagi.shopkart.order.service.CheckoutService;
import com.mkinagi.shopkart.support.IntegrationTest;

/**
 * A flash sale: many customers check out the last few units at the same instant. Pessimistic row locks
 * must serialise the stock decrement so exactly {@code stock} orders succeed and stock never goes
 * negative.
 */
class CheckoutConcurrencyIntegrationTest extends IntegrationTest {

	private static final int STOCK = 5;

	private static final int BUYERS = 25;

	@Autowired
	CheckoutService checkoutService;

	@Autowired
	CartService cartService;

	@Test
	void concurrentCheckoutsNeverOversell() throws Exception {
		Long category = createCategory(unique("FlashSale"), null);
		Long product = createProduct(category, "Flash Sale Console", "29999.00", STOCK);
		Long other = createProduct(category, "Controller", "4999.00", 1000);

		List<Long> buyers = new ArrayList<>();
		for (int i = 0; i < BUYERS; i++) {
			Long userId = registerCustomer().id();
			// Half the carts also contain a second product, so transactions lock overlapping row sets.
			cartService.addItem(userId, product, 1);
			if (i % 2 == 0) {
				cartService.addItem(userId, other, 1);
			}
			buyers.add(userId);
		}

		CheckoutRequest request = new CheckoutRequest(null,
				new AddressInput("Buyer", "9876543210", "1 Main Road", null, "Pune", "Maharashtra", "411001", "IN"),
				PaymentMethod.UPI);
		AtomicInteger succeeded = new AtomicInteger();
		Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(BUYERS)) {
			List<Future<?>> futures = new ArrayList<>();
			for (Long userId : buyers) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						checkoutService.checkout(userId, request, null);
						succeeded.incrementAndGet();
					}
					catch (ApiException ex) {
						failures.computeIfAbsent(ex.getCode(), k -> new AtomicInteger()).incrementAndGet();
					}
					return null;
				}));
			}
			start.countDown();
			for (Future<?> f : futures) {
				f.get();
			}
		}

		assertThat(succeeded.get()).isEqualTo(STOCK);
		assertThat(failures).containsOnlyKeys("INSUFFICIENT_STOCK");
		assertThat(failures.get("INSUFFICIENT_STOCK").get()).isEqualTo(BUYERS - STOCK);
		getJson("/api/v1/products/" + product, null).andExpect(jsonPath("$.stockQuantity").value(0));
	}

}
