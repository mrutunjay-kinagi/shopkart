package com.mkinagi.shopkart;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against throw-away containers: {@code ./mvnw spring-boot:test-run}.
 */
public class TestShopkartApplication {

	public static void main(String[] args) {
		SpringApplication.from(ShopkartApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
