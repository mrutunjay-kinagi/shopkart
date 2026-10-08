package com.mkinagi.shopkart.catalog.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryList;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryRequest;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryResponse;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductDetails;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductRequest;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductSummary;
import com.mkinagi.shopkart.catalog.domain.Category;
import com.mkinagi.shopkart.catalog.domain.CategoryRepository;
import com.mkinagi.shopkart.catalog.domain.Product;
import com.mkinagi.shopkart.catalog.domain.ProductRepository;
import com.mkinagi.shopkart.common.PageResponse;
import com.mkinagi.shopkart.common.Slugs;
import com.mkinagi.shopkart.common.error.ApiException;

/**
 * Catalogue reads (cached) and administrative writes (which evict the cache).
 */
@Service
public class ProductService {

	public static final String PRODUCTS_CACHE = "products";

	public static final String CATEGORIES_CACHE = "categories";

	private final ProductRepository products;

	private final CategoryRepository categories;

	private final ProductSearchRepository search;

	public ProductService(ProductRepository products, CategoryRepository categories, ProductSearchRepository search) {
		this.products = products;
		this.categories = categories;
		this.search = search;
	}

	/**
	 * Product page lookup. Served from Redis after the first read; the miss path costs three queries
	 * (product + category, images, specifications).
	 */
	@Cacheable(cacheNames = PRODUCTS_CACHE, key = "#id")
	@Transactional(readOnly = true)
	public ProductDetails get(Long id) {
		Product product = products.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Product", id));
		return ProductDetails.from(product);
	}

	/**
	 * The same lookup without the cache: self-invocation bypasses the caching proxy, so this always hits
	 * MySQL. Used by the benchmark to measure what the cache saves.
	 */
	@Transactional(readOnly = true)
	public ProductDetails getUncached(Long id) {
		return get(id);
	}

	@Transactional(readOnly = true)
	public Long idForSlug(String slug) {
		return products.findIdBySlug(slug).orElseThrow(() -> ApiException.notFound("Product", slug));
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductSummary> search(ProductSearchQuery query) {
		List<Long> categoryIds = null;
		if (query.categorySlug() != null && !query.categorySlug().isBlank()) {
			Category category = categories.findBySlug(query.categorySlug())
				.orElseThrow(() -> ApiException.notFound("Category", query.categorySlug()));
			categoryIds = withDescendants(category.getId());
		}
		return search.search(query, categoryIds);
	}

	@Cacheable(cacheNames = CATEGORIES_CACHE, key = "'all'")
	@Transactional(readOnly = true)
	public CategoryList categories() {
		return new CategoryList(categories.findAll().stream().map(CategoryResponse::from).toList());
	}

	@CacheEvict(cacheNames = CATEGORIES_CACHE, allEntries = true)
	@Transactional
	public CategoryResponse createCategory(CategoryRequest request) {
		String slug = Slugs.of(request.name());
		if (categories.existsBySlug(slug)) {
			throw ApiException.conflict("CATEGORY_EXISTS", "Category '" + request.name() + "' already exists");
		}
		if (request.parentId() != null && !categories.existsById(request.parentId())) {
			throw ApiException.notFound("Category", request.parentId());
		}
		return CategoryResponse
			.from(categories.save(new Category(request.name().trim(), slug, request.description(), request.parentId())));
	}

	@Transactional
	public ProductDetails create(ProductRequest request) {
		if (products.existsBySku(request.sku())) {
			throw ApiException.conflict("SKU_EXISTS", "A product with SKU " + request.sku() + " already exists");
		}
		Product product = new Product(request.sku(), Slugs.of(request.name(), request.sku()));
		apply(product, request);
		product.setStockQuantity(request.stockQuantity());
		return ProductDetails.from(products.save(product));
	}

	@CacheEvict(cacheNames = PRODUCTS_CACHE, key = "#id")
	@Transactional
	public ProductDetails update(Long id, ProductRequest request) {
		Product product = load(id);
		if (!product.getSku().equals(request.sku())) {
			throw ApiException.badRequest("SKU_IMMUTABLE", "SKU cannot be changed");
		}
		apply(product, request);
		product.setStockQuantity(request.stockQuantity());
		return ProductDetails.from(product);
	}

	@CacheEvict(cacheNames = PRODUCTS_CACHE, key = "#id")
	@Transactional
	public ProductDetails adjustStock(Long id, int delta) {
		Product product = products.lockAllById(List.of(id))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Product", id));
		int updated = product.getStockQuantity() + delta;
		if (updated < 0) {
			throw ApiException.unprocessable("NEGATIVE_STOCK", "Stock cannot go below zero");
		}
		product.setStockQuantity(updated);
		return ProductDetails.from(product);
	}

	@CacheEvict(cacheNames = PRODUCTS_CACHE, key = "#id")
	@Transactional
	public void setActive(Long id, boolean active) {
		load(id).setActive(active);
	}

	private void apply(Product product, ProductRequest r) {
		if (r.price().compareTo(r.mrp()) > 0) {
			throw ApiException.badRequest("PRICE_ABOVE_MRP", "Price cannot exceed MRP");
		}
		Category category = categories.findById(r.categoryId())
			.orElseThrow(() -> ApiException.notFound("Category", r.categoryId()));
		product.update(r.name().trim(), r.brand(), r.description(), r.price(), r.mrp(), category,
				r.images() != null ? r.images() : List.of(), r.specifications() != null ? r.specifications() : Map.of());
	}

	private Product load(Long id) {
		return products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id));
	}

	private List<Long> withDescendants(Long rootId) {
		List<Long> ids = new ArrayList<>();
		List<Long> frontier = List.of(rootId);
		while (!frontier.isEmpty() && ids.size() < 500) {
			ids.addAll(frontier);
			frontier = frontier.stream().flatMap(id -> categories.findByParentId(id).stream()).map(Category::getId).toList();
		}
		return ids;
	}

}
