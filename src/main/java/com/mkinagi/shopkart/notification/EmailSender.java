package com.mkinagi.shopkart.notification;

/**
 * Delivery channel. SMTP locally (Mailpit) and Amazon SES in production are both just implementations.
 */
public interface EmailSender {

	void send(String to, String subject, String body);

}
