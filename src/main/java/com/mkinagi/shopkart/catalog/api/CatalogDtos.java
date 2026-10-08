package com.mkinagi.shopkart.catalog.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mkinagi.shopkart.catalog.domain.Category;
import com.mkinagi.shopkart.catalog.domain.Product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

	private CatalogDtos() {
	}

	public record CategoryResponse(Long id, String name, String slug, String description, Long parentId) {

		public static CategoryResponse from(Category c) {
			return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getParentId());
		}

	}

	/** Wrapper so the category list can be cached with a concrete JSON type. */
	public record CategoryList(List<CategoryResponse> items) {
	}

	public record CategoryRef(Long id, String name, String slug) {
	}

	public record ProductSummary(Long id, String name, String slug, String brand, BigDecimal price, BigDecimal mrp,
			int discountPercent, boolean inStock, String categorySlug, String imageUrl) {
	}

	/** Full product page. Cached in Redis, so it must stay a plain serialisable value. */
	public record ProductDetails(Long id, String sku, String name, String slug, String brand, String description,
			BigDecimal price, BigDecimal mrp, int discountPercent, int stockQuantity, boolean active,
			CategoryRef category, List<String> images, Map<String, String> specifications) {

		public static ProductDetails from(Product p) {
			Category c = p.getCategory();
			return new ProductDetails(p.getId(), p.getSku(), p.getName(), p.getSlug(), p.getBrand(), p.getDescription(),
					p.getPrice(), p.getMrp(), CatalogDtos.discountPercent(p.getPrice(), p.getMrp()), p.getStockQuantity(),
					p.isActive(), new CategoryRef(c.getId(), c.getName(), c.getSlug()), List.copyOf(p.getImages()),
					new LinkedHashMap<>(p.getSpecifications()));
		}

		public boolean inStock() {
			return active && stockQuantity > 0;
		}

	}

	public static int discountPercent(BigDecimal price, BigDecimal mrp) {
		if (mrp == null || mrp.signum() == 0 || price.compareTo(mrp) >= 0) {
			return 0;
		}
		return mrp.subtract(price).multiply(BigDecimal.valueOf(100)).divide(mrp, 0, RoundingMode.DOWN).intValue();
	}

	public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description,
			Long parentId) {
	}

	public record ProductRequest(
			@NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,40}$", message = "must be 3-40 upper-case letters, digits or '-'") String sku,
			@NotBlank @Size(max = 200) String name, @Size(max = 80) String brand,
			@NotBlank @Size(max = 10_000) String description,
			@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
			@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal mrp,
			@Min(0) @Max(1_000_000) int stockQuantity, @NotNull Long categoryId,
			@Size(max = 10) List<@NotBlank @Size(max = 500) String> images,
			@Size(max = 40) Map<@NotBlank @Size(max = 80) String, @NotBlank @Size(max = 500) String> specifications) {
	}

	public record StockAdjustmentRequest(@Min(-1_000_000) @Max(1_000_000) int delta) {
	}

}
