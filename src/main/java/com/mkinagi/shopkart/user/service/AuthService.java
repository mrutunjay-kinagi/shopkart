package com.mkinagi.shopkart.user.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mkinagi.shopkart.common.error.ApiException;
import com.mkinagi.shopkart.common.events.DomainEventPublisher;
import com.mkinagi.shopkart.common.events.EventPayloads.UserRegistered;
import com.mkinagi.shopkart.common.events.EventTypes;
import com.mkinagi.shopkart.common.events.Topics;
import com.mkinagi.shopkart.config.ShopkartProperties;
import com.mkinagi.shopkart.user.api.AuthDtos.AuthResponse;
import com.mkinagi.shopkart.user.api.AuthDtos.LoginRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.RegisterRequest;
import com.mkinagi.shopkart.user.api.AuthDtos.UserResponse;
import com.mkinagi.shopkart.user.domain.PasswordResetToken;
import com.mkinagi.shopkart.user.domain.PasswordResetTokenRepository;
import com.mkinagi.shopkart.user.domain.RefreshToken;
import com.mkinagi.shopkart.user.domain.RefreshTokenRepository;
import com.mkinagi.shopkart.user.domain.Role;
import com.mkinagi.shopkart.user.domain.User;
import com.mkinagi.shopkart.user.domain.UserRepository;

/**
 * Registration, login, token refresh/rotation, logout and password reset.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository users;

	private final RefreshTokenRepository refreshTokens;

	private final PasswordResetTokenRepository resetTokens;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final AccessTokenDenylist denylist;

	private final LoginRateLimiter rateLimiter;

	private final DomainEventPublisher events;

	private final PasswordResetNotifier resetNotifier;

	private final ShopkartProperties.Jwt settings;

	/** Compared against when the e-mail is unknown so both paths cost one hash verification. */
	private final String dummyHash;

	public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
			PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder, TokenService tokenService,
			AccessTokenDenylist denylist, LoginRateLimiter rateLimiter, DomainEventPublisher events,
			PasswordResetNotifier resetNotifier, ShopkartProperties properties) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.resetTokens = resetTokens;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.denylist = denylist;
		this.rateLimiter = rateLimiter;
		this.events = events;
		this.resetNotifier = resetNotifier;
		this.settings = properties.jwt();
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalize(request.email());
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
		}
		User user;
		try {
			user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()),
					request.fullName().trim(), request.phone(), Role.CUSTOMER));
		}
		catch (DataIntegrityViolationException ex) {
			// Two concurrent registrations raced past the exists check; the unique index decided.
			throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
		}
		events.publish(Topics.USER_EVENTS, "User", user.getId(), EventTypes.USER_REGISTERED,
				new UserRegistered(user.getId(), user.getEmail(), user.getFullName()));
		return toResponse(user, tokenService.issue(user, null));
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		String email = normalize(request.email());
		rateLimiter.check(email);
		Optional<User> user = users.findByEmail(email);
		String hash = user.map(User::getPasswordHash).orElse(dummyHash);
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !matches || !user.get().isEnabled()) {
			throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
		}
		rateLimiter.reset(email);
		return toResponse(user.get(), tokenService.issue(user.get(), null));
	}

	/**
	 * Exchanges a refresh token for a new pair. The presented token is single-use: replaying one that
	 * was already rotated is treated as theft and revokes every token from that login.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public AuthResponse refresh(String rawRefreshToken) {
		Instant now = Instant.now();
		RefreshToken current = refreshTokens.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
			.orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
		if (current.getReplacedBy() != null) {
			int revoked = refreshTokens.revokeFamily(current.getFamilyId(), now);
			log.warn("Refresh token reuse detected for user {}; revoked {} tokens", current.getUserId(), revoked);
			throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "Refresh token has already been used");
		}
		if (!current.isActive(now)) {
			throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is expired or revoked");
		}
		User user = users.findById(current.getUserId())
			.filter(User::isEnabled)
			.orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
		TokenService.TokenPair pair = tokenService.issue(user, current.getFamilyId());
		current.revoke(now, pair.storedRefreshToken().getId());
		return toResponse(user, pair);
	}

	@Transactional
	public void logout(Jwt accessToken, String rawRefreshToken) {
		denylist.revoke(accessToken.getId(), accessToken.getExpiresAt());
		if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
			Long userId = Long.valueOf(accessToken.getSubject());
			refreshTokens.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
				.filter(token -> token.getUserId().equals(userId))
				.ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
		}
	}

	/**
	 * Always succeeds from the caller's point of view so the endpoint cannot be used to discover which
	 * e-mail addresses have accounts.
	 */
	@Transactional
	public void requestPasswordReset(String rawEmail) {
		users.findByEmail(normalize(rawEmail)).filter(User::isEnabled).ifPresent(user -> {
			String rawToken = SecureTokens.newToken();
			resetTokens.save(new PasswordResetToken(user.getId(), SecureTokens.sha256(rawToken),
					Instant.now().plus(settings.passwordResetTtl())));
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					resetNotifier.sendResetLink(user.getId(), user.getEmail(), user.getFullName(), rawToken);
				}
			});
		});
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		Instant now = Instant.now();
		PasswordResetToken token = resetTokens.findByTokenHash(SecureTokens.sha256(rawToken))
			.filter(t -> t.isUsable(now))
			.orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "Reset link is invalid or has expired"));
		User user = users.findById(token.getUserId())
			.orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "Reset link is invalid or has expired"));
		user.changePasswordHash(passwordEncoder.encode(newPassword));
		token.markUsed(now);
		// Sign the account out everywhere: whoever knew the old password keeps no session.
		refreshTokens.revokeAllForUser(user.getId(), now);
	}

	private AuthResponse toResponse(User user, TokenService.TokenPair pair) {
		return new AuthResponse(pair.accessToken(), "Bearer", settings.accessTokenTtl().toSeconds(),
				pair.refreshToken(), UserResponse.from(user));
	}

	static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
