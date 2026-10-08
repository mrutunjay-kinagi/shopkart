package com.mkinagi.shopkart.cart.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mkinagi.shopkart.cart.api.CartDtos.AddItemRequest;
import com.mkinagi.shopkart.cart.api.CartDtos.CartView;
import com.mkinagi.shopkart.cart.api.CartDtos.UpdateQuantityRequest;
import com.mkinagi.shopkart.cart.service.CartService;
import com.mkinagi.shopkart.common.web.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "The signed-in user's shopping cart")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	@Operation(summary = "Review cart with live prices, stock checks and totals")
	public CartView view(@AuthenticationPrincipal Jwt jwt) {
		return cartService.view(CurrentUser.id(jwt));
	}

	@PostMapping("/items")
	@Operation(summary = "Add a product (quantities accumulate)")
	public CartView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItemRequest request) {
		return cartService.addItem(CurrentUser.id(jwt), request.productId(), request.quantity());
	}

	@PutMapping("/items/{productId}")
	@Operation(summary = "Set the quantity of a cart line")
	public CartView update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId,
			@Valid @RequestBody UpdateQuantityRequest request) {
		return cartService.updateQuantity(CurrentUser.id(jwt), productId, request.quantity());
	}

	@DeleteMapping("/items/{productId}")
	@Operation(summary = "Remove a cart line")
	public CartView remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
		return cartService.removeItem(CurrentUser.id(jwt), productId);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Empty the cart")
	public void clear(@AuthenticationPrincipal Jwt jwt) {
		cartService.clear(CurrentUser.id(jwt));
	}

}
