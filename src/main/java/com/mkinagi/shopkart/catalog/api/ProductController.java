package com.mkinagi.shopkart.catalog.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryResponse;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductDetails;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductSummary;
import com.mkinagi.shopkart.catalog.service.ProductSearchQuery;
import com.mkinagi.shopkart.catalog.service.ProductService;
import com.mkinagi.shopkart.common.PageResponse;
import com.mkinagi.shopkart.common.error.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Catalogue", description = "Public product browsing, search and details")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping("/categories")
	@Operation(summary = "List all categories (flat, with parent ids)")
	public List<CategoryResponse> categories() {
		return productService.categories().items();
	}

	@GetMapping("/products")
	@Operation(summary = "Browse by category and search by keyword with filters, sorting and pagination")
	public PageResponse<ProductSummary> search(
			@Parameter(description = "Keywords, matched by prefix against name, brand and description") @RequestParam(required = false) @Size(max = 100) String q,
			@Parameter(description = "Category slug; includes sub-categories") @RequestParam(required = false) String category,
			@RequestParam(required = false) String brand, @RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(defaultValue = "false") boolean inStock,
			@Parameter(description = "RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST or NAME") @RequestParam(required = false) ProductSearchQuery.Sort sort,
			@RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice must not exceed maxPrice");
		}
		return productService
			.search(new ProductSearchQuery(q, category, brand, minPrice, maxPrice, inStock, sort, page, size));
	}

	@GetMapping("/products/{id}")
	@Operation(summary = "Product details with images and specifications")
	public ResponseEntity<ProductDetails> get(@PathVariable Long id) {
		return withCacheHeaders(productService.get(id));
	}

	@GetMapping("/products/slug/{slug}")
	@Operation(summary = "Product details by SEO slug")
	public ResponseEntity<ProductDetails> getBySlug(@PathVariable String slug) {
		return withCacheHeaders(productService.get(productService.idForSlug(slug)));
	}

	private static ResponseEntity<ProductDetails> withCacheHeaders(ProductDetails details) {
		if (!details.active()) {
			throw ApiException.notFound("Product", details.id());
		}
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
			.body(details);
	}

}
