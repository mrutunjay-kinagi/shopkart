package com.mkinagi.shopkart.catalog.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryRequest;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.CategoryResponse;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductDetails;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.ProductRequest;
import com.mkinagi.shopkart.catalog.api.CatalogDtos.StockAdjustmentRequest;
import com.mkinagi.shopkart.catalog.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: Catalogue", description = "Catalogue management (ADMIN role)")
@SecurityRequirement(name = "bearerAuth")
public class AdminCatalogController {

	private final ProductService productService;

	public AdminCatalogController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping("/categories")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a category")
	public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
		return productService.createCategory(request);
	}

	@PostMapping("/products")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a product")
	public ProductDetails create(@Valid @RequestBody ProductRequest request) {
		return productService.create(request);
	}

	@PutMapping("/products/{id}")
	@Operation(summary = "Replace a product's details")
	public ProductDetails update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
		return productService.update(id, request);
	}

	@PatchMapping("/products/{id}/stock")
	@Operation(summary = "Adjust stock by a positive or negative delta")
	public ProductDetails adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
		return productService.adjustStock(id, request.delta());
	}

	@DeleteMapping("/products/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Deactivate (soft-delete) a product")
	public void deactivate(@PathVariable Long id) {
		productService.setActive(id, false);
	}

}
