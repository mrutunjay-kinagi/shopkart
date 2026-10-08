package com.mkinagi.shopkart.catalog.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mkinagi.shopkart.common.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

	@Column(nullable = false, unique = true, updatable = false)
	private String sku;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	private String brand;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal mrp;

	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false)
	private boolean active = true;

	@Version
	private long version;

	@ElementCollection
	@CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
	@OrderColumn(name = "position")
	@Column(name = "url", nullable = false)
	private List<String> images = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "product_specifications", joinColumns = @JoinColumn(name = "product_id"))
	@MapKeyColumn(name = "spec_key")
	@Column(name = "spec_value", nullable = false)
	private Map<String, String> specifications = new LinkedHashMap<>();

	protected Product() {
	}

	public Product(String sku, String slug) {
		this.sku = sku;
		this.slug = slug;
	}

	public void update(String name, String brand, String description, BigDecimal price, BigDecimal mrp,
			Category category, List<String> images, Map<String, String> specifications) {
		this.name = name;
		this.brand = brand;
		this.description = description;
		this.price = price;
		this.mrp = mrp;
		this.category = category;
		this.images.clear();
		this.images.addAll(images);
		this.specifications.clear();
		this.specifications.putAll(specifications);
	}

	public boolean canFulfil(int quantity) {
		return active && stockQuantity >= quantity;
	}

	public void decreaseStock(int quantity) {
		if (quantity <= 0 || stockQuantity < quantity) {
			throw new IllegalStateException("Insufficient stock for product " + getId());
		}
		stockQuantity -= quantity;
	}

	public void increaseStock(int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Quantity must be positive");
		}
		stockQuantity += quantity;
	}

	public void setStockQuantity(int stockQuantity) {
		this.stockQuantity = stockQuantity;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getBrand() {
		return brand;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public BigDecimal getMrp() {
		return mrp;
	}

	public int getStockQuantity() {
		return stockQuantity;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isActive() {
		return active;
	}

	public List<String> getImages() {
		return images;
	}

	public Map<String, String> getSpecifications() {
		return specifications;
	}

}
