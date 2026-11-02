CREATE TABLE users
(
    id_user SERIAL PRIMARY KEY,
    name    VARCHAR(50)  NOT NULL,
    email   VARCHAR(100) NOT NULL UNIQUE,
    role    VARCHAR(15)  NOT NULL
);


CREATE TABLE menu
(
    id_position  SERIAL PRIMARY KEY,
    name_product VARCHAR(50)    NOT NULL,
    price        NUMERIC(10, 2) NOT NULL
);


CREATE TABLE taste_tags
(
    id_tag SERIAL PRIMARY KEY,
    name   VARCHAR(50) NOT NULL UNIQUE
);


CREATE TABLE visits
(
    id_visit     SERIAL PRIMARY KEY,
    id_user      INTEGER     NOT NULL,
    table_number INTEGER,
    opened_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at    TIMESTAMP,
    status       VARCHAR(10) NOT NULL,

    FOREIGN KEY (id_user)
        REFERENCES users (id_user)
);


CREATE TABLE orders
(
    id_order     SERIAL PRIMARY KEY,
    id_visit     INTEGER        NOT NULL,
    id_waiter    INTEGER,
    id_worker    INTEGER,
    total_amount NUMERIC(10, 2) NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_visit)
        REFERENCES visits (id_visit),

    FOREIGN KEY (id_waiter)
        REFERENCES users (id_user),

    FOREIGN KEY (id_worker)
        REFERENCES users (id_user)
);


CREATE TABLE order_items
(
    id_order_item SERIAL PRIMARY KEY,
    id_order      INTEGER NOT NULL,
    id_product    INTEGER NOT NULL,
    quantity      INTEGER NOT NULL,
    unit_price    NUMERIC(10, 2) NOT NULL,

    FOREIGN KEY (id_order)
        REFERENCES orders (id_order),

    FOREIGN KEY (id_product)
        REFERENCES menu (id_position)
);


CREATE TABLE menu_taste_tags
(
    id_position INTEGER NOT NULL,
    id_tag      INTEGER NOT NULL,

    PRIMARY KEY (id_position, id_tag),

    FOREIGN KEY (id_position)
        REFERENCES menu (id_position),

    FOREIGN KEY (id_tag)
        REFERENCES taste_tags (id_tag)
);


CREATE TABLE visit_taste_preferences
(
    id_visit INTEGER NOT NULL,
    id_tag   INTEGER NOT NULL,

    PRIMARY KEY (id_visit, id_tag),

    FOREIGN KEY (id_visit)
        REFERENCES visits (id_visit),

    FOREIGN KEY (id_tag)
        REFERENCES taste_tags (id_tag)
);