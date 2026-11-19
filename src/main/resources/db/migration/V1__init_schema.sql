-- Table: products
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table: flash_sales
CREATE TABLE flash_sales (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    total_stock INT NOT NULL CHECK (total_stock >= 0),
    available_stock INT NOT NULL CHECK (available_stock >= 0 AND available_stock <= total_stock),
    reserved_stock INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    max_per_user INT NOT NULL DEFAULT 2 CHECK (max_per_user > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_stock_balance CHECK (available_stock + reserved_stock <= total_stock)
);

-- Table: reservations
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    flash_sale_id UUID NOT NULL REFERENCES flash_sales(id),
    user_id VARCHAR(128) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservations_status_expires_at ON reservations (status, expires_at);
CREATE INDEX idx_reservations_user_sale ON reservations (user_id, flash_sale_id);

-- Table: orders
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    reservation_id UUID UNIQUE NOT NULL REFERENCES reservations(id),
    user_id VARCHAR(128) NOT NULL,
    total_amount_cents BIGINT NOT NULL CHECK (total_amount_cents >= 0),
    status VARCHAR(32) NOT NULL,
    payment_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table: idempotency_keys
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Table: outbox_events
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);
