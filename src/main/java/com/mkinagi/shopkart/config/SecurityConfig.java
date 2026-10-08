package com.mkinagi.shopkart.config;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.mkinagi.shopkart.common.error.GlobalExceptionHandler;
import com.mkinagi.shopkart.user.service.AccessTokenDenylist;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless JWT security. Access tokens are short-lived HS256 JWTs validated locally on every request;
 * a Redis denylist makes logout take effect immediately instead of waiting for token expiry.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_POST = { "/api/v1/auth/register", "/api/v1/auth/login",
			"/api/v1/auth/refresh", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset",
			"/api/v1/payments/webhooks/**" };

	private static final String[] PUBLIC_GET = { "/api/v1/products/**", "/api/v1/categories/**",
			"/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**",
			"/swagger-ui.html" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, PUBLIC_POST)
				.permitAll()
				.requestMatchers(HttpMethod.GET, PUBLIC_GET)
				.permitAll()
				.requestMatchers("/api/v1/admin/**")
				.hasRole("ADMIN")
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint((request, response, ex) -> writeProblem(response, objectMapper,
						HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", describe(ex)))
				.accessDeniedHandler((request, response, ex) -> writeProblem(response, objectMapper,
						HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action")))
			.exceptionHandling(e -> e
				.authenticationEntryPoint((request, response, ex) -> writeProblem(response, objectMapper,
						HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required"))
				.accessDeniedHandler((request, response, ex) -> writeProblem(response, objectMapper,
						HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action")));
		return http.build();
	}

	private static String describe(AuthenticationException ex) {
		return ex.getMessage() != null && ex.getMessage().contains("revoked") ? "Token has been revoked"
				: "Invalid or expired access token";
	}

	private static void writeProblem(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
			String code, String detail) throws java.io.IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.problem(status, code, detail));
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	SecretKey jwtSigningKey(ShopkartProperties properties) {
		byte[] key = Base64.getDecoder().decode(properties.jwt().secret());
		if (key.length < 32) {
			throw new IllegalStateException("shopkart.jwt.secret must decode to at least 256 bits");
		}
		return new SecretKeySpec(key, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, ShopkartProperties properties, AccessTokenDenylist denylist) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		OAuth2TokenValidator<Jwt> notRevoked = jwt -> denylist.isRevoked(jwt.getId())
				? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has been revoked", null))
				: OAuth2TokenValidatorResult.success();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()), notRevoked));
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(ShopkartProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.web().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
		config.setExposedHeaders(List.of("X-Request-Id", "Location"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
