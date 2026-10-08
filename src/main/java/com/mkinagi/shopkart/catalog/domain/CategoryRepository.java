package com.mkinagi.shopkart.catalog.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	Optional<Category> findBySlug(String slug);

	List<Category> findByParentId(Long parentId);

	boolean existsBySlug(String slug);

}
