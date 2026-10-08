-- ShopKart core schema (MySQL 8.x, InnoDB, utf8mb4)

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    phone          VARCHAR(20),
    role           VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE addresses (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    label          VARCHAR(40)  NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    line1          VARCHAR(200) NOT NULL,
    line2          VARCHAR(200),
    city           VARCHAR(80)  NOT NULL,
    state          VARCHAR(80)  NOT NULL,
    postal_code    VARCHAR(12)  NOT NULL,
    country        VARCHAR(2)   NOT NULL DEFAULT 'IN',
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_addresses_user ON addresses (user_id);

-- Opaque refresh tokens; only the SHA-256 hash is stored.
CREATE TABLE refresh_tokens (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    family_id    CHAR(36)    NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    revoked_at   DATETIME(6),
    replaced_by  BIGINT,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE password_reset_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6),
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------- catalog
CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(80)  NOT NULL,
    slug        VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    parent_id   BIGINT,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE TABLE products (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    sku            VARCHAR(40)   NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    slug           VARCHAR(220)  NOT NULL,
    brand          VARCHAR(80),
    description    TEXT          NOT NULL,
    price          DECIMAL(12,2) NOT NULL,
    mrp            DECIMAL(12,2) NOT NULL,
    stock_quantity INT           NOT NULL,
    category_id    BIGINT        NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_price CHECK (price >= 0 AND price <= mrp)
);
-- Browsing: list active products of a category ordered by price / recency.
CREATE INDEX idx_products_category_active_price ON products (category_id, active, price);
-- Keyword search (see ProductRepository#search).
CREATE FULLTEXT INDEX ftx_products_search ON products (name, brand, description);

CREATE TABLE product_images (
    product_id BIGINT       NOT NULL,
    position   INT          NOT NULL,
    url        VARCHAR(500) NOT NULL,
    PRIMARY KEY (product_id, position),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE product_specifications (
    product_id BIGINT       NOT NULL,
    spec_key   VARCHAR(80)  NOT NULL,
    spec_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (product_id, spec_key),
    CONSTRAINT fk_product_specs_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------- orders
CREATE TABLE orders (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    order_number       VARCHAR(30)   NOT NULL,
    user_id            BIGINT        NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    payment_method     VARCHAR(20)   NOT NULL,
    subtotal           DECIMAL(12,2) NOT NULL,
    shipping_fee       DECIMAL(12,2) NOT NULL,
    total_amount       DECIMAL(12,2) NOT NULL,
    currency           CHAR(3)       NOT NULL DEFAULT 'INR',
    idempotency_key    VARCHAR(64),
    ship_name          VARCHAR(120)  NOT NULL,
    ship_phone         VARCHAR(20)   NOT NULL,
    ship_line1         VARCHAR(200)  NOT NULL,
    ship_line2         VARCHAR(200),
    ship_city          VARCHAR(80)   NOT NULL,
    ship_state         VARCHAR(80)   NOT NULL,
    ship_postal_code   VARCHAR(12)   NOT NULL,
    ship_country       VARCHAR(2)    NOT NULL,
    carrier            VARCHAR(40),
    tracking_number    VARCHAR(40),
    payment_due_at     DATETIME(6),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);
-- Order history: newest first for a user.
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
-- Expiry sweep of unpaid orders.
CREATE INDEX idx_orders_status_due ON orders (status, payment_due_at);

CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    sku          VARCHAR(40)   NOT NULL,
    product_name VARCHAR(200)  NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    quantity     INT           NOT NULL,
    line_total   DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_order_items_qty CHECK (quantity > 0)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    note        VARCHAR(255),
    changed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, changed_at);

-- ---------------------------------------------------------------- payments
CREATE TABLE payments (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    user_id            BIGINT        NOT NULL,
    method             VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    amount             DECIMAL(12,2) NOT NULL,
    currency           CHAR(3)       NOT NULL DEFAULT 'INR',
    gateway            VARCHAR(30)   NOT NULL,
    gateway_reference  VARCHAR(64),
    instrument_summary VARCHAR(60),
    failure_reason     VARCHAR(255),
    receipt_number     VARCHAR(30),
    paid_at            DATETIME(6),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_gateway_ref UNIQUE (gateway, gateway_reference),
    CONSTRAINT uk_payments_receipt UNIQUE (receipt_number),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_payments_order ON payments (order_id);

-- ---------------------------------------------------------------- integration
-- Transactional outbox: rows are written in the same transaction as the business
-- change and relayed to Kafka by OutboxRelay.
CREATE TABLE outbox_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_id       CHAR(36)     NOT NULL,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   VARCHAR(40)  NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    topic          VARCHAR(80)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6),
    attempts       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, id);

-- Consumers record handled event ids to make processing idempotent.
CREATE TABLE processed_events (
    consumer    VARCHAR(60) NOT NULL,
    event_id    CHAR(36)    NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT,
    channel     VARCHAR(10)  NOT NULL,
    recipient   VARCHAR(254) NOT NULL,
    template    VARCHAR(60)  NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    status      VARCHAR(10)  NOT NULL,
    error       VARCHAR(255),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at);
