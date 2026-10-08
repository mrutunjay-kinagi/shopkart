package com.mkinagi.shopkart.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mkinagi.shopkart.catalog.service.ProductSearchRepositoryAccess;

class ProductSearchQueryTest {

	@Test
	void freeTextBecomesSafeRequiredPrefixTerms() {
		assertThat(ProductSearchRepositoryAccess.toBooleanQuery("Gaming  Laptop")).isEqualTo("+gaming* +laptop*");
		// Boolean-mode operators in user input are stripped, not interpreted.
		assertThat(ProductSearchRepositoryAccess.toBooleanQuery("-sony +\"bose\" (qc*)")).isEqualTo("+sony* +bose*");
		// Words below InnoDB's minimum token size are dropped; nothing left means no full-text clause.
		assertThat(ProductSearchRepositoryAccess.toBooleanQuery("tv 4k")).isNull();
		assertThat(ProductSearchRepositoryAccess.toBooleanQuery("  ")).isNull();
	}

}
