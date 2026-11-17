CREATE TABLE menu (
    id_position SERIAL PRIMARY KEY,
    name_product VARCHAR(50) NOT NULL,
    price NUMERIC(10, 2) NOT NULL
);

CREATE TABLE taste_tags (
    id_tag SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE menu_taste_tags (
    id_position INTEGER NOT NULL REFERENCES menu (id_position),
    id_tag INTEGER NOT NULL REFERENCES taste_tags (id_tag),
    PRIMARY KEY (id_position, id_tag)
);
