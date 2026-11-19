-- Deterministic seed product
INSERT INTO products (id, sku, name, price_cents, created_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'SNEAKER-LTD-001',
    'Limited Edition Cyber Sneakers',
    25000,
    NOW()
);

-- Deterministic active flash sale
INSERT INTO flash_sales (id, product_id, total_stock, available_stock, reserved_stock, max_per_user, starts_at, ends_at, version, created_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    100,
    100,
    0,
    2,
    NOW() - INTERVAL '1 hour',
    NOW() + INTERVAL '24 hours',
    0,
    NOW()
);
