package com.mkinagi.shopkart.catalog.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@EntityGraph(attributePaths = { "category", "images" })
	Optional<Product> findWithDetailsById(Long id);

	@Query("select p.id from Product p where p.slug = :slug")
	Optional<Long> findIdBySlug(@Param("slug") String slug);

	boolean existsBySku(String sku);

	/**
	 * {@code SELECT ... FOR UPDATE} on every product in the cart. Rows are locked in primary-key order so
	 * two checkouts sharing products always acquire locks in the same order and cannot deadlock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select p from Product p where p.id in :ids order by p.id")
	List<Product> lockAllById(@Param("ids") Collection<Long> ids);

}
