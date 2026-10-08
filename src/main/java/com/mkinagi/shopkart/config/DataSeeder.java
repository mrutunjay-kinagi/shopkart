package com.mkinagi.shopkart.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mkinagi.shopkart.catalog.domain.Category;
import com.mkinagi.shopkart.catalog.domain.CategoryRepository;
import com.mkinagi.shopkart.catalog.domain.Product;
import com.mkinagi.shopkart.catalog.domain.ProductRepository;
import com.mkinagi.shopkart.common.Slugs;
import com.mkinagi.shopkart.user.domain.Role;
import com.mkinagi.shopkart.user.domain.User;
import com.mkinagi.shopkart.user.domain.UserRepository;

/**
 * Demo data for local runs ({@code shopkart.seed.enabled=true}): a category tree, a small catalogue and an
 * administrator account. Idempotent - it does nothing once categories exist.
 */
@Component
@ConditionalOnProperty(name = "shopkart.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final CategoryRepository categories;

	private final ProductRepository products;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final String adminEmail;

	private final String adminPassword;

	public DataSeeder(CategoryRepository categories, ProductRepository products, UserRepository users,
			PasswordEncoder passwordEncoder, @Value("${shopkart.seed.admin-email}") String adminEmail,
			@Value("${shopkart.seed.admin-password}") String adminPassword) {
		this.categories = categories;
		this.products = products;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!users.existsByEmail(adminEmail)) {
			users.save(new User(adminEmail, passwordEncoder.encode(adminPassword), "ShopKart Admin", null, Role.ADMIN));
			log.info("Seeded administrator {}", adminEmail);
		}
		if (categories.count() > 0) {
			return;
		}
		Category electronics = category("Electronics", "Phones, laptops, audio and accessories", null);
		Category phones = category("Mobiles", "Smartphones and feature phones", electronics);
		Category laptops = category("Laptops", "Notebooks and ultrabooks", electronics);
		Category audio = category("Audio", "Headphones, earbuds and speakers", electronics);
		Category fashion = category("Fashion", "Clothing and footwear", null);
		Category footwear = category("Footwear", "Shoes, sandals and sneakers", fashion);
		Category menswear = category("Men's Clothing", "Shirts, t-shirts and trousers", fashion);
		Category home = category("Home & Kitchen", "Appliances, cookware and decor", null);
		Category kitchen = category("Kitchen Appliances", "Mixers, kettles and cookers", home);
		Category books = category("Books", "Fiction, non-fiction and technical books", null);

		product("MOB-PX9-128", "Pixel 9 5G (128 GB, Obsidian)", "Google", phones, "64999", "79999", 40,
				"Google Tensor G4 smartphone with a 6.3-inch Actua display, 50 MP camera and seven years of OS updates.",
				Map.of("Display", "6.3-inch OLED, 120 Hz", "Storage", "128 GB", "RAM", "12 GB", "Battery", "4700 mAh"));
		product("MOB-GS24-256", "Galaxy S24 (256 GB, Marble Grey)", "Samsung", phones, "69999", "89999", 35,
				"Compact flagship with Galaxy AI features, a 50 MP triple camera and an all-day battery.",
				Map.of("Display", "6.2-inch Dynamic AMOLED 2X", "Storage", "256 GB", "RAM", "8 GB", "Battery", "4000 mAh"));
		product("MOB-RN13-128", "Redmi Note 13 5G (128 GB, Arctic White)", "Xiaomi", phones, "17999", "20999", 120,
				"Budget 5G phone with a 108 MP camera, 120 Hz AMOLED display and 33 W fast charging.",
				Map.of("Display", "6.67-inch AMOLED", "Storage", "128 GB", "RAM", "6 GB", "Battery", "5000 mAh"));
		product("MOB-IP16-128", "iPhone 16 (128 GB, Ultramarine)", "Apple", phones, "79900", "79900", 25,
				"A18 chip, Camera Control button and a 48 MP Fusion camera.",
				Map.of("Display", "6.1-inch Super Retina XDR", "Storage", "128 GB", "Chip", "A18"));
		product("LAP-MBA-M3", "MacBook Air 13 M3 (8 GB / 256 GB)", "Apple", laptops, "99900", "114900", 15,
				"Fanless ultraportable laptop with the Apple M3 chip and up to 18 hours of battery life.",
				Map.of("Processor", "Apple M3", "RAM", "8 GB", "Storage", "256 GB SSD", "Weight", "1.24 kg"));
		product("LAP-TP-E14", "ThinkPad E14 Gen 5 (Core i5, 16 GB)", "Lenovo", laptops, "62990", "78990", 20,
				"Business laptop with a 14-inch WUXGA display, spill-resistant keyboard and MIL-STD durability.",
				Map.of("Processor", "Intel Core i5-1335U", "RAM", "16 GB", "Storage", "512 GB SSD", "Weight", "1.41 kg"));
		product("LAP-ROG-G16", "ROG Strix G16 Gaming Laptop (RTX 4060)", "ASUS", laptops, "124990", "149990", 8,
				"Gaming laptop with a 165 Hz display, Intel Core i7 processor and NVIDIA RTX 4060 graphics.",
				Map.of("Processor", "Intel Core i7-13650HX", "GPU", "RTX 4060 8 GB", "RAM", "16 GB", "Display", "16-inch 165 Hz"));
		product("AUD-WH1000XM5", "WH-1000XM5 Wireless Noise Cancelling Headphones", "Sony", audio, "26990", "34990", 50,
				"Industry-leading noise cancellation with 30-hour battery life and multipoint Bluetooth.",
				Map.of("Type", "Over-ear", "Battery", "30 hours", "Connectivity", "Bluetooth 5.2"));
		product("AUD-APP2", "AirPods Pro (2nd generation, USB-C)", "Apple", audio, "22900", "24900", 60,
				"Active noise cancellation, adaptive audio and a MagSafe charging case.",
				Map.of("Type", "In-ear", "Battery", "6 hours (30 with case)", "Charging", "USB-C / MagSafe"));
		product("AUD-JBL-FLIP6", "JBL Flip 6 Portable Bluetooth Speaker", "JBL", audio, "9999", "14999", 75,
				"Waterproof IP67 portable speaker with bold JBL Original Pro Sound and 12 hours of playtime.",
				Map.of("Battery", "12 hours", "Rating", "IP67", "Output", "30 W"));
		product("FTW-NK-PEG41", "Air Zoom Pegasus 41 Running Shoes", "Nike", footwear, "11895", "11895", 45,
				"Responsive everyday running shoe with ReactX foam and Air Zoom units.",
				Map.of("Use", "Road running", "Closure", "Lace-up", "Sizes", "UK 6-12"));
		product("FTW-AD-ULTRA", "Ultraboost Light Running Shoes", "Adidas", footwear, "13999", "17999", 30,
				"Lightest Ultraboost ever with Light BOOST cushioning and a Continental rubber outsole.",
				Map.of("Use", "Road running", "Closure", "Lace-up", "Sizes", "UK 6-11"));
		product("FTW-BATA-OXF", "Formal Leather Oxford Shoes", "Bata", footwear, "2499", "3299", 80,
				"Classic black leather oxfords with a cushioned insole for all-day office wear.",
				Map.of("Material", "Genuine leather", "Closure", "Lace-up", "Sizes", "UK 6-11"));
		product("MEN-LP-OXSH", "Slim Fit Cotton Oxford Shirt", "Louis Philippe", menswear, "1799", "2499", 100,
				"Breathable 100% cotton oxford shirt with a button-down collar.",
				Map.of("Fabric", "100% cotton", "Fit", "Slim", "Sleeve", "Full"));
		product("MEN-LEVI-511", "511 Slim Fit Jeans", "Levi's", menswear, "2999", "4299", 90,
				"Slim-fit jeans sitting below the waist with a slim leg and stretch denim.",
				Map.of("Fabric", "98% cotton, 2% elastane", "Fit", "Slim", "Rise", "Mid"));
		product("KIT-PH-MIXER", "750 W Mixer Grinder with 3 Jars", "Philips", kitchen, "3999", "5995", 55,
				"Powerful 750 W motor with stainless steel jars for grinding, blending and chutneys.",
				Map.of("Power", "750 W", "Jars", "3", "Warranty", "2 years"));
		product("KIT-PIG-KETTLE", "1.5 L Electric Kettle", "Pigeon", kitchen, "699", "1195", 150,
				"Stainless steel electric kettle with auto shut-off and boil-dry protection.",
				Map.of("Capacity", "1.5 L", "Power", "1500 W", "Material", "Stainless steel"));
		product("KIT-PRES-COOK", "Svachh 5 L Pressure Cooker", "Prestige", kitchen, "2299", "2950", 70,
				"Aluminium pressure cooker with a spillage-control lid, suitable for gas and induction.",
				Map.of("Capacity", "5 L", "Material", "Aluminium", "Induction", "Yes"));
		product("BK-DDIA", "Designing Data-Intensive Applications", "O'Reilly", books, "1299", "1899", 40,
				"Martin Kleppmann's guide to the principles behind reliable, scalable and maintainable data systems.",
				Map.of("Author", "Martin Kleppmann", "Pages", "616", "Language", "English"));
		product("BK-CLEAN-ARCH", "Clean Architecture", "Pearson", books, "599", "899", 60,
				"Robert C. Martin on the rules of software architecture and component design.",
				Map.of("Author", "Robert C. Martin", "Pages", "432", "Language", "English"));
		product("BK-SPRING-ACT", "Spring in Action, Sixth Edition", "Manning", books, "2499", "2999", 0,
				"Craig Walls' hands-on guide to building applications with Spring and Spring Boot.",
				Map.of("Author", "Craig Walls", "Pages", "520", "Language", "English"));
		log.info("Seeded {} categories and {} products", categories.count(), products.count());
	}

	private Category category(String name, String description, Category parent) {
		return categories.save(new Category(name, Slugs.of(name), description, parent != null ? parent.getId() : null));
	}

	private void product(String sku, String name, String brand, Category category, String price, String mrp, int stock,
			String description, Map<String, String> specs) {
		Product product = new Product(sku, Slugs.of(name, sku));
		product.update(name, brand, description, new BigDecimal(price), new BigDecimal(mrp), category,
				List.of("https://cdn.shopkart.local/products/" + sku.toLowerCase() + "/1.jpg",
						"https://cdn.shopkart.local/products/" + sku.toLowerCase() + "/2.jpg"),
				new LinkedHashMap<>(specs));
		product.setStockQuantity(stock);
		products.save(product);
	}

}
