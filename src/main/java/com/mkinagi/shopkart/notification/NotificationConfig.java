package com.mkinagi.shopkart.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
class NotificationConfig {

	@Bean
	EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
			@Value("${shopkart.notifications.from:ShopKart <no-reply@shopkart.local>}") String from) {
		JavaMailSender sender = mailSender.getIfAvailable();
		return sender != null ? new SmtpEmailSender(sender, from) : new LoggingEmailSender();
	}

}
