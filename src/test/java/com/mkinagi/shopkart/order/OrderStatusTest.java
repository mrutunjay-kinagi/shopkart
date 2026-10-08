package com.mkinagi.shopkart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.order.domain.Order;
import com.mkinagi.shopkart.order.domain.OrderStatus;
import com.mkinagi.shopkart.order.domain.ShippingAddress;

class OrderStatusTest {

	@ParameterizedTest
	@CsvSource({ "PENDING_PAYMENT,CONFIRMED,true", "PENDING_PAYMENT,CANCELLED,true", "PENDING_PAYMENT,SHIPPED,false",
			"CONFIRMED,SHIPPED,true", "CONFIRMED,CANCELLED,true", "SHIPPED,CANCELLED,false", "SHIPPED,DELIVERED,true",
			"OUT_FOR_DELIVERY,DELIVERED,true", "DELIVERED,CANCELLED,false", "CANCELLED,CONFIRMED,false" })
	void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}

	@Test
	void orderRecordsTimelineAndRejectsIllegalMoves() {
		Order order = new Order("SK-1", 1L, PaymentMethod.CARD,
				new ShippingAddress("A", "9876543210", "L1", null, "C", "S", "560001", "IN"), null, Instant.now());
		order.transitionTo(OrderStatus.CONFIRMED, "paid");
		assertThat(order.getPaymentDueAt()).isNull();
		assertThatThrownBy(() -> order.transitionTo(OrderStatus.PENDING_PAYMENT, "back"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("cannot move from CONFIRMED to PENDING_PAYMENT");
		assertThat(order.getHistory()).extracting(h -> h.getStatus())
			.containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);
	}

}
