package com.mkinagi.shopkart.payment.gateway;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.mkinagi.shopkart.config.ShopkartProperties;

/**
 * Deterministic sandbox gateway that behaves like a real one:
 * <ul>
 * <li>CARD - synchronous; token {@code tok_<brand>_<last4>} succeeds, {@code tok_fail_*} is declined.</li>
 * <li>NETBANKING - synchronous; bank code {@code FAIL} is declined.</li>
 * <li>UPI - asynchronous; returns PENDING and the result arrives later via a signed webhook, like a UPI
 * collect request approved in the customer's app.</li>
 * </ul>
 * Swapping in a real processor means another {@link PaymentGateway} bean; nothing else changes.
 */
@Component
public class MockPayGateway implements PaymentGateway {

	public static final String NAME = "mockpay";

	private static final Pattern CARD_TOKEN = Pattern.compile("^tok_([a-z]+)_(\\d{4})$");

	private static final Pattern VPA = Pattern.compile("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z]{2,32}$");

	private final byte[] webhookSecret;

	public MockPayGateway(ShopkartProperties properties) {
		this.webhookSecret = properties.payments().mockpayWebhookSecret().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public ChargeResult charge(ChargeRequest request) {
		String reference = "mp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		return switch (request.method()) {
			case CARD -> chargeCard(request.cardToken(), reference);
			case NETBANKING -> {
				if (request.bankCode() == null || request.bankCode().isBlank()) {
					yield new ChargeResult(Outcome.FAILED, reference, null, "Bank code is required");
				}
				String bank = request.bankCode().toUpperCase(Locale.ROOT);
				yield "FAIL".equals(bank) ? new ChargeResult(Outcome.FAILED, reference, "Netbanking " + bank,
						"Authorisation declined by bank")
						: new ChargeResult(Outcome.SUCCEEDED, reference, "Netbanking " + bank, null);
			}
			case UPI -> request.upiVpa() != null && VPA.matcher(request.upiVpa()).matches()
					? new ChargeResult(Outcome.PENDING, reference, "UPI " + maskVpa(request.upiVpa()), null)
					: new ChargeResult(Outcome.FAILED, reference, null, "Invalid UPI id");
			case COD -> throw new IllegalArgumentException("Cash on delivery is not charged online");
		};
	}

	private static ChargeResult chargeCard(String token, String reference) {
		if (token == null) {
			return new ChargeResult(Outcome.FAILED, reference, null, "Card token is required");
		}
		if (token.startsWith("tok_fail")) {
			return new ChargeResult(Outcome.FAILED, reference, null, "Card declined by issuer");
		}
		var matcher = CARD_TOKEN.matcher(token);
		if (!matcher.matches()) {
			return new ChargeResult(Outcome.FAILED, reference, null, "Invalid card token");
		}
		String summary = matcher.group(1).toUpperCase(Locale.ROOT) + " **** " + matcher.group(2);
		return new ChargeResult(Outcome.SUCCEEDED, reference, summary, null);
	}

	@Override
	public RefundResult refund(String gatewayReference, BigDecimal amount) {
		return new RefundResult(true, "rf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
	}

	@Override
	public boolean verifyWebhookSignature(byte[] body, String signature) {
		if (signature == null) {
			return false;
		}
		byte[] expected = HexFormat.of().formatHex(hmac(body)).getBytes(StandardCharsets.US_ASCII);
		// Constant-time comparison so the signature cannot be guessed byte by byte from response timing.
		return MessageDigest.isEqual(expected, signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
	}

	public String sign(byte[] body) {
		return HexFormat.of().formatHex(hmac(body));
	}

	private byte[] hmac(byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
			return mac.doFinal(body);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String maskVpa(String vpa) {
		int at = vpa.indexOf('@');
		String user = vpa.substring(0, at);
		return (user.length() <= 2 ? user : user.substring(0, 2) + "***") + vpa.substring(at);
	}

}
