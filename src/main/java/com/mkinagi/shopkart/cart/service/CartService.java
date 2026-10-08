package com.mkinagi.shopkart.cart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.mkinagi.shopkart.cart.api.CartDtos.CartLine;
import com.mkinagi.shopkart.cart.api.CartDtos.CartView;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductDetails;
import com.mkinagi.shopkart.catalog.service.ProductService;
import com.mkinagi.shopkart.common.PricingPolicy;
import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.common.events.EventEnvelope;
import com.mkinagi.shopkart.common.events.EventPayloads.CartItemAdded;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.Topics;
import com.mkinagi.shopkart.config.ShopkartProperties;

import tools.jackson.databind.ObjectMapper;

@Service
public class CartService {

	private static final Logger log = LoggerFactory.getLogger(CartService.class);

	private final CartStore store;

	private final ProductService products;

	private final PricingPolicy pricing;

	private final KafkaTemplate<String, String> kafka;

	private final ObjectMapper objectMapper;

	private final ShopkartProperties.Cart limits;

	public CartService(CartStore store, ProductService products, PricingPolicy pricing,
			KafkaTemplate<String, String> kafka, ObjectMapper objectMapper, ShopkartProperties properties) {
		this.store = store;
		this.products = products;
		this.pricing = pricing;
		this.kafka = kafka;
		this.objectMapper = objectMapper;
		this.limits = properties.cart();
	}

	public CartView view(Long userId) {
		List<CartLine> lines = new ArrayList<>();
		BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
		BigDecimal savings = BigDecimal.ZERO.setScale(2);
		int totalQuantity = 0;
		boolean ready = true;
		for (Map.Entry<Long, Integer> entry : store.items(userId).entrySet()) {
			ProductDetails product;
			try {
				product = products.get(entry.getKey());
			}
			catch (ApiException ex) {
				// Product was removed from the catalogue after being added; drop the stale line.
				store.remove(userId, entry.getKey());
				continue;
			}
			int quantity = entry.getValue();
			String issue = null;
			if (!product.inStock()) {
				issue = "OUT_OF_STOCK";
			}
			else if (product.stockQuantity() < quantity) {
				issue = "ONLY_" + product.stockQuantity() + "_LEFT";
			}
			BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(quantity));
			if (issue == null) {
				subtotal = subtotal.add(lineTotal);
				savings = savings.add(product.mrp().subtract(product.price()).multiply(BigDecimal.valueOf(quantity)));
				totalQuantity += quantity;
			}
			else {
				ready = false;
			}
			lines.add(new CartLine(product.id(), product.name(), product.slug(),
					product.images().isEmpty() ? null : product.images().get(0), product.price(), product.mrp(),
					quantity, lineTotal, issue == null, product.stockQuantity(), issue));
		}
		BigDecimal shipping = pricing.shippingFee(subtotal);
		return new CartView(lines, totalQuantity, subtotal, savings, shipping, subtotal.add(shipping), "INR",
				ready && !lines.isEmpty());
	}

	public CartView addItem(Long userId, Long productId, int quantity) {
		int current = store.quantity(userId, productId);
		if (current == 0 && store.distinctItems(userId) >= limits.maxDistinctItems()) {
			throw ApiException.unprocessable("CART_FULL", "A cart can hold at most " + limits.maxDistinctItems() + " products");
		}
		setQuantity(userId, productId, current + quantity);
		publishItemAdded(userId, productId, quantity);
		return view(userId);
	}

	public CartView updateQuantity(Long userId, Long productId, int quantity) {
		if (store.quantity(userId, productId) == 0) {
			throw ApiException.notFound("Cart item", productId);
		}
		setQuantity(userId, productId, quantity);
		return view(userId);
	}

	public CartView removeItem(Long userId, Long productId) {
		store.remove(userId, productId);
		return view(userId);
	}

	public void clear(Long userId) {
		store.clear(userId);
	}

	public Map<Long, Integer> items(Long userId) {
		return store.items(userId);
	}

	private void setQuantity(Long userId, Long productId, int quantity) {
		ProductDetails product = products.get(productId);
		if (!product.active()) {
			throw ApiException.notFound("Product", productId);
		}
		if (quantity > limits.maxQuantityPerItem()) {
			throw ApiException.unprocessable("QUANTITY_LIMIT",
					"At most " + limits.maxQuantityPerItem() + " units of a product per order");
		}
		if (quantity > product.stockQuantity()) {
			throw ApiException.conflict("INSUFFICIENT_STOCK",
					"Only " + product.stockQuantity() + " unit(s) of '" + product.name() + "' available");
		}
		store.put(userId, productId, quantity);
	}

	/**
	 * Cart activity is analytics, not a business transaction, so it is sent straight to Kafka without the
	 * outbox: losing an event is acceptable and must never fail the user's request.
	 */
	private void publishItemAdded(Long userId, Long productId, int quantity) {
		try {
			EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(),
					EventTypes.CART_ITEM_ADDED, "Cart", userId.toString(), Instant.now(),
					objectMapper.valueToTree(new CartItemAdded(userId, productId, quantity)));
			kafka.send(Topics.CART_EVENTS, userId.toString(), objectMapper.writeValueAsString(envelope))
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.warn("Could not publish cart event for user {}", userId, ex);
					}
				});
		}
		catch (RuntimeException ex) {
			log.warn("Could not publish cart event for user {}", userId, ex);
		}
	}

}
