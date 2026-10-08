package com.mkinagi.shopkart.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback when no SMTP server is configured: logs the subject only (bodies may contain reset links).
 */
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String body) {
		log.info("E-mail to {}: {}", to, subject);
	}

}
