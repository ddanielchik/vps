CREATE TABLE users
(
    id_user SERIAL PRIMARY KEY,
    name    VARCHAR(50)  NOT NULL,
    email   VARCHAR(100) NOT NULL UNIQUE,
    role    VARCHAR(15)  NOT NULL
);
