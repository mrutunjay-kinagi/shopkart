package com.mkinagi.shopkart.order.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Human-friendly, non-sequential order numbers such as {@code SK-261008-7KQ2MX}. Not exposing the
 * database id prevents competitors from estimating order volume.
 */
@Component
public class OrderNumberGenerator {

	private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

	private final SecureRandom random = new SecureRandom();

	public String next() {
		StringBuilder suffix = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			suffix.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return "SK-" + LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DATE) + "-" + suffix;
	}

}
