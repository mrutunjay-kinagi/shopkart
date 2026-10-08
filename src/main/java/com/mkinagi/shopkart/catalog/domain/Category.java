package com.mkinagi.shopkart.catalog.domain;

import com.mkinagi.shopkart.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	private String description;

	@Column(name = "parent_id")
	private Long parentId;

	protected Category() {
	}

	public Category(String name, String slug, String description, Long parentId) {
		this.name = name;
		this.slug = slug;
		this.description = description;
		this.parentId = parentId;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getDescription() {
		return description;
	}

	public Long getParentId() {
		return parentId;
	}

}
