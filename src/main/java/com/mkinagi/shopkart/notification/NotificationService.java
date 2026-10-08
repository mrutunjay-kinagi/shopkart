package com.mkinagi.shopkart.notification;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mkinagi.shopkart.config.ShopkartProperties;
import com.mkinagi.shopkart.user.service.PasswordResetNotifier;
import com.mkinagi.shopkart.user.service.UserDirectory;
import com.mkinagi.shopkart.user.service.UserDirectory.UserContact;

/**
 * Sends e-mails and records each attempt. Also the in-process adapter for password reset links.
 */
@Service
public class NotificationService implements PasswordResetNotifier {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final EmailSender sender;

	private final NotificationRepository notifications;

	private final UserDirectory users;

	private final String frontendBaseUrl;

	public NotificationService(EmailSender sender, NotificationRepository notifications, UserDirectory users,
			ShopkartProperties properties) {
		this.sender = sender;
		this.notifications = notifications;
		this.users = users;
		this.frontendBaseUrl = properties.web().frontendBaseUrl();
	}

	void sendToUser(Long userId, Function<String, EmailTemplates.Email> template) {
		UserContact contact = users.contact(userId);
		send(userId, contact.email(), template.apply(contact.fullName()));
	}

	@Override
	public void sendResetLink(Long userId, String email, String fullName, String rawToken) {
		String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
		send(userId, email, EmailTemplates.passwordReset(fullName, link));
	}

	private void send(Long userId, String to, EmailTemplates.Email email) {
		try {
			sender.send(to, email.subject(), email.body());
			notifications.save(new Notification(userId, to, email.template(), email.subject(), "SENT", null));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to send {} to user {}", email.template(), userId, ex);
			notifications.save(new Notification(userId, to, email.template(), email.subject(), "FAILED",
					truncate(ex.getMessage())));
		}
	}

	private static String truncate(String message) {
		return message == null ? null : message.length() > 250 ? message.substring(0, 250) : message;
	}

}
