package com.mkinagi.shopkart.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingPolicyTest {

	@ParameterizedTest
	@CsvSource({ "0.00,0.00", "1.00,49.00", "498.99,49.00", "499.00,0.00", "15000.00,0.00" })
	void freeShippingFromThreshold(String subtotal, String expectedFee) {
		assertThat(new PricingPolicy().shippingFee(new BigDecimal(subtotal))).isEqualByComparingTo(expectedFee);
	}

}
