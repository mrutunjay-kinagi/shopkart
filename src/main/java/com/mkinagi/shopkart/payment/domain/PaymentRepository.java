package com.mkinagi.shopkart.payment.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

	boolean existsByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Payment> findByGatewayAndGatewayReference(String gateway, String gatewayReference);

	Optional<Payment> findByIdAndUserId(Long id, Long userId);

}
