package com.mkinagi.shopkart.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByIdAndUserId(Long id, Long userId);

	Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

	Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

	List<Order> findByStatusAndPaymentDueAtBeforeOrderByPaymentDueAtAsc(OrderStatus status, Instant cutoff,
			Limit limit);

}
