package com.mkinagi.shopkart.catalog.service;

/** Exposes package-private helpers of {@link ProductSearchRepository} to tests in other packages. */
public final class ProductSearchRepositoryAccess {

	private ProductSearchRepositoryAccess() {
	}

	public static String toBooleanQuery(String q) {
		return ProductSearchRepository.toBooleanQuery(q);
	}

}
