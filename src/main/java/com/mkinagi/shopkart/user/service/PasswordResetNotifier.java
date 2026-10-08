package com.mkinagi.shopkart.user.service;

/**
 * Port implemented by the notification module. The reset link carries a secret, so it is handed over
 * in-process after commit rather than broadcast on Kafka where every consumer could read it.
 */
public interface PasswordResetNotifier {

	void sendResetLink(Long userId, String email, String fullName, String rawToken);

}
