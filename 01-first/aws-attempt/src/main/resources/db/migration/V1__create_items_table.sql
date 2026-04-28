CREATE TABLE items (
  id          BIGSERIAL    PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  description VARCHAR(1000),
  created_at  TIMESTAMPTZ  NOT NULL,
  updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_items_name ON items (LOWER(name));
