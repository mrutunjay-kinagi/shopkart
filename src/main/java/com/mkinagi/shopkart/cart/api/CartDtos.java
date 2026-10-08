package com.mkinagi.shopkart.cart.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CartDtos {

	private CartDtos() {
	}

	public record AddItemRequest(@NotNull Long productId, @Min(1) @Max(100) int quantity) {
	}

	public record UpdateQuantityRequest(@Min(1) @Max(100) int quantity) {
	}

	public record CartLine(Long productId, String name, String slug, String imageUrl, BigDecimal unitPrice,
			BigDecimal mrp, int quantity, BigDecimal lineTotal, boolean available, int stockQuantity, String issue) {
	}

	/**
	 * Cart review: every amount is computed server-side from current catalogue prices. {@code checkoutReady}
	 * is false while any line is unavailable or exceeds stock.
	 */
	public record CartView(List<CartLine> items, int totalQuantity, BigDecimal subtotal, BigDecimal savings,
			BigDecimal shippingFee, BigDecimal total, String currency, boolean checkoutReady) {
	}

}
