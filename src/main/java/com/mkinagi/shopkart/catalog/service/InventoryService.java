package com.mkinagi.shopkart.catalog.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mkinagi.shopkart.catalog.domain.Product;
import com.mkinagi.shopkart.catalog.domain.ProductRepository;
import com.mkinagi.shopkart.common.error.ApiException;

/**
 * The catalogue module's stock API for checkout and cancellation. Runs inside the caller's transaction so
 * that stock changes commit or roll back together with the order.
 */
@Service
public class InventoryService {

	private final ProductRepository products;

	private final CacheManager cacheManager;

	public InventoryService(ProductRepository products, CacheManager cacheManager) {
		this.products = products;
		this.cacheManager = cacheManager;
	}

	/**
	 * Locks the requested products, verifies availability and decrements stock. Prices are read from the
	 * locked rows, never from the client or the cache, so the order is charged the current price.
	 * @param quantities product id to requested quantity
	 * @return the priced lines, in product id order
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<ReservedLine> reserve(Map<Long, Integer> quantities) {
		Map<Long, Product> locked = products.lockAllById(quantities.keySet())
			.stream()
			.collect(Collectors.toMap(Product::getId, Function.identity()));
		List<ReservedLine> lines = new ArrayList<>();
		for (Map.Entry<Long, Integer> entry : quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			Product product = locked.get(entry.getKey());
			int quantity = entry.getValue();
			if (product == null || !product.isActive()) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCT_UNAVAILABLE",
						"Product " + entry.getKey() + " is no longer available");
			}
			if (!product.canFulfil(quantity)) {
				throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Only " + product.getStockQuantity()
						+ " unit(s) of '" + product.getName() + "' left in stock");
			}
			product.decreaseStock(quantity);
			lines.add(new ReservedLine(product.getId(), product.getSku(), product.getName(), product.getPrice(),
					quantity));
		}
		evictAfterCommit(quantities.keySet());
		return lines;
	}

	/** Returns stock for a cancelled or expired order. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void release(Map<Long, Integer> quantities) {
		for (Product product : products.lockAllById(quantities.keySet())) {
			product.increaseStock(quantities.get(product.getId()));
		}
		evictAfterCommit(quantities.keySet());
	}

	/**
	 * Cached product pages show stock, so drop them once the new stock level is visible to other
	 * transactions. Evicting earlier would let a concurrent read re-cache the old value.
	 */
	private void evictAfterCommit(Iterable<Long> ids) {
		Cache cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
		if (cache == null) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				ids.forEach(cache::evict);
			}
		});
	}

	public record ReservedLine(Long productId, String sku, String name, BigDecimal unitPrice, int quantity) {

		public BigDecimal lineTotal() {
			return unitPrice.multiply(BigDecimal.valueOf(quantity));
		}

	}

}
