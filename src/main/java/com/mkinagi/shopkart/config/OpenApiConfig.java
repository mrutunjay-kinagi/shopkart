package com.mkinagi.shopkart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI shopkartOpenApi() {
		return new OpenAPI()
			.info(new Info().title("ShopKart E-commerce API")
				.version("v1")
				.description("Backend for the ShopKart e-commerce platform: accounts, catalogue, cart, checkout, "
						+ "payments, order tracking and notifications.")
				.contact(new Contact().name("Mrutunjay Kinagi")))
			.components(new Components().addSecuritySchemes("bearerAuth",
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

}
