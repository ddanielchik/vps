CREATE TABLE visits (
    id_visit SERIAL PRIMARY KEY,
    id_user INTEGER NOT NULL,
    table_number INTEGER,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    status VARCHAR(10) NOT NULL
);
CREATE TABLE orders (
    id_order SERIAL PRIMARY KEY,
    id_visit INTEGER NOT NULL REFERENCES visits (id_visit),
    id_waiter INTEGER,
    id_worker INTEGER,
    total_amount NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_items (
    id_order_item SERIAL PRIMARY KEY,
    id_order INTEGER NOT NULL REFERENCES orders (id_order),
    id_product INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL
);
CREATE TABLE visit_taste_preferences (
    id_visit INTEGER NOT NULL REFERENCES visits (id_visit),
    id_tag INTEGER NOT NULL,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id_visit, id_tag)
);
CREATE INDEX visits_user_idx ON visits (id_user);
CREATE INDEX orders_waiter_idx ON orders (id_waiter);
CREATE INDEX orders_worker_idx ON orders (id_worker);
CREATE INDEX order_items_product_idx ON order_items (id_product);
