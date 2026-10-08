package com.mkinagi.shopkart.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {

	private Slugs() {
	}

	public static String of(String... parts) {
		String joined = String.join(" ", parts);
		String ascii = Normalizer.normalize(joined, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return slug.length() > 200 ? slug.substring(0, 200) : slug;
	}

}
