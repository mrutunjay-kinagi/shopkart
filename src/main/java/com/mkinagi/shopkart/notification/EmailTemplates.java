package com.mkinagi.shopkart.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Collectors;

import com.mkinagi.shopkart.common.events.EventPayloads.OrderPlaced;

/**
 * Plain-text e-mail bodies. Kept in code for the capstone; a production system would use a template
 * engine or SES templates so marketing can edit copy without a deployment.
 */
final class EmailTemplates {

	private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

	private EmailTemplates() {
	}

	record Email(String template, String subject, String body) {
	}

	static Email welcome(String name) {
		return new Email("WELCOME", "Welcome to ShopKart, " + name + "!", """
				Hi %s,

				Your ShopKart account is ready. Happy shopping!

				- Team ShopKart""".formatted(name));
	}

	static Email passwordReset(String name, String link) {
		return new Email("PASSWORD_RESET", "Reset your ShopKart password", """
				Hi %s,

				We received a request to reset your password. This link is valid for 15 minutes and can be used once:

				%s

				If you did not ask for this, you can ignore this e-mail; your password will not change.

				- Team ShopKart""".formatted(name, link));
	}

	static Email orderPlaced(String name, OrderPlaced order) {
		String lines = order.items()
			.stream()
			.map(l -> "  %d x %s @ %s".formatted(l.quantity(), l.name(), money(l.unitPrice())))
			.collect(Collectors.joining("\n"));
		String next = "COD".equals(order.paymentMethod()) ? "Please keep the amount ready at delivery."
				: "Complete your payment within 15 minutes to confirm the order.";
		return new Email("ORDER_PLACED", "Order " + order.orderNumber() + " received", """
				Hi %s,

				Thank you for your order %s.

				%s

				Total: %s
				%s

				- Team ShopKart""".formatted(name, order.orderNumber(), lines, money(order.totalAmount()), next));
	}

	static Email orderConfirmed(String name, String orderNumber) {
		return new Email("ORDER_CONFIRMED", "Order " + orderNumber + " confirmed", """
				Hi %s,

				Your order %s is confirmed and will be packed shortly. We will e-mail you when it ships.

				- Team ShopKart""".formatted(name, orderNumber));
	}

	static Email orderShipped(String name, String orderNumber, String carrier, String trackingNumber) {
		return new Email("ORDER_SHIPPED", "Order " + orderNumber + " has shipped", """
				Hi %s,

				Your order %s is on its way with %s. Tracking number: %s

				- Team ShopKart""".formatted(name, orderNumber, carrier, trackingNumber));
	}

	static Email orderDelivered(String name, String orderNumber) {
		return new Email("ORDER_DELIVERED", "Order " + orderNumber + " delivered", """
				Hi %s,

				Your order %s has been delivered. We hope you enjoy it!

				- Team ShopKart""".formatted(name, orderNumber));
	}

	static Email orderCancelled(String name, String orderNumber, String reason) {
		return new Email("ORDER_CANCELLED", "Order " + orderNumber + " cancelled", """
				Hi %s,

				Your order %s has been cancelled (%s). Any amount paid will be refunded to the original payment method.

				- Team ShopKart""".formatted(name, orderNumber, reason));
	}

	static Email paymentReceipt(String name, String receiptNumber, BigDecimal amount, String method) {
		return new Email("PAYMENT_RECEIPT", "Payment receipt " + receiptNumber, """
				Hi %s,

				We received your payment of %s by %s.
				Receipt number: %s

				- Team ShopKart""".formatted(name, money(amount), method, receiptNumber));
	}

	static Email paymentFailed(String name, String reason) {
		return new Email("PAYMENT_FAILED", "Your payment did not go through", """
				Hi %s,

				Your payment failed: %s. You can retry from your orders page before the payment window closes.

				- Team ShopKart""".formatted(name, reason));
	}

	static Email refund(String name, BigDecimal amount) {
		return new Email("PAYMENT_REFUNDED", "Refund initiated", """
				Hi %s,

				A refund of %s has been initiated and should reach you in 5-7 working days.

				- Team ShopKart""".formatted(name, money(amount)));
	}

	private static String money(BigDecimal amount) {
		return INR.format(amount);
	}

}
