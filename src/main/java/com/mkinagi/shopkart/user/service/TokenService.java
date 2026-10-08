package com.mkinagi.shopkart.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.mkinagi.shopkart.config.ShopkartProperties;
import com.mkinagi.shopkart.user.domain.RefreshToken;
import com.mkinagi.shopkart.user.domain.RefreshTokenRepository;
import com.mkinagi.shopkart.user.domain.User;

/**
 * Issues the token pair returned by login and refresh.
 */
@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final RefreshTokenRepository refreshTokens;

	private final ShopkartProperties.Jwt settings;

	public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, ShopkartProperties properties) {
		this.encoder = encoder;
		this.refreshTokens = refreshTokens;
		this.settings = properties.jwt();
	}

	public TokenPair issue(User user, String familyId) {
		Instant now = Instant.now();
		Instant accessExpiry = now.plus(settings.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(settings.issuer())
			.subject(String.valueOf(user.getId()))
			.id(UUID.randomUUID().toString())
			.issuedAt(now)
			.expiresAt(accessExpiry)
			.claim("email", user.getEmail())
			.claim("name", user.getFullName())
			.claim("roles", List.of(user.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

		String refreshToken = SecureTokens.newToken();
		RefreshToken stored = refreshTokens.save(new RefreshToken(user.getId(), SecureTokens.sha256(refreshToken),
				familyId != null ? familyId : UUID.randomUUID().toString(), now.plus(settings.refreshTokenTtl())));
		return new TokenPair(accessToken, accessExpiry, refreshToken, stored);
	}

	public record TokenPair(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
			RefreshToken storedRefreshToken) {
	}

}
