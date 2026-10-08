package com.mkinagi.shopkart.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.mkinagi.shopkart.common.PaymentMethod;
import com.mkinagi.shopkart.config.ShopkartProperties;
import com.mkinagi.shopkart.payment.gateway.MockPayGateway;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway.ChargeRequest;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway.ChargeResult;
import com.mkinagi.shopkart.payment.gateway.PaymentGateway.Outcome;

class MockPayGatewayTest {

	MockPayGateway gateway = new MockPayGateway(new ShopkartProperties(
			new ShopkartProperties.Jwt("x".repeat(44), "shopkart", Duration.ofMinutes(15), Duration.ofDays(7),
					Duration.ofMinutes(15)),
			new ShopkartProperties.Payments("test-secret"), null, null, null, null));

	@Test
	void cardTokensDecideTheOutcome() {
		assertThat(charge(PaymentMethod.CARD, "tok_visa_4242", null, null).outcome()).isEqualTo(Outcome.SUCCEEDED);
		assertThat(charge(PaymentMethod.CARD, "tok_visa_4242", null, null).instrumentSummary())
			.isEqualTo("VISA **** 4242");
		assertThat(charge(PaymentMethod.CARD, "tok_fail_stolen", null, null).outcome()).isEqualTo(Outcome.FAILED);
		assertThat(charge(PaymentMethod.CARD, "4111111111111111", null, null).failureReason())
			.isEqualTo("Invalid card token");
	}

	@Test
	void upiIsAsynchronousAndMasked() {
		var result = charge(PaymentMethod.UPI, null, "rahul.sharma@oksbi", null);
		assertThat(result.outcome()).isEqualTo(Outcome.PENDING);
		assertThat(result.instrumentSummary()).isEqualTo("UPI ra***@oksbi");
		assertThat(charge(PaymentMethod.UPI, null, "no-at-sign", null).outcome()).isEqualTo(Outcome.FAILED);
	}

	@Test
	void netbankingFailsOnlyForTheFailBank() {
		assertThat(charge(PaymentMethod.NETBANKING, null, null, "icic").outcome()).isEqualTo(Outcome.SUCCEEDED);
		assertThat(charge(PaymentMethod.NETBANKING, null, null, "FAIL").outcome()).isEqualTo(Outcome.FAILED);
	}

	@Test
	void webhookSignaturesAreVerified() {
		byte[] body = "{\"gatewayReference\":\"mp_1\",\"status\":\"SUCCEEDED\"}".getBytes(StandardCharsets.UTF_8);
		String signature = gateway.sign(body);
		assertThat(gateway.verifyWebhookSignature(body, signature)).isTrue();
		assertThat(gateway.verifyWebhookSignature(body, signature.toUpperCase())).isTrue();
		assertThat(gateway.verifyWebhookSignature(body, null)).isFalse();
		body[5] ^= 1;
		assertThat(gateway.verifyWebhookSignature(body, signature)).isFalse();
	}

	private ChargeResult charge(PaymentMethod method, String card,
			String vpa, String bank) {
		return gateway.charge(new ChargeRequest("1", new BigDecimal("100.00"), "INR", method, card, vpa, bank));
	}

}
