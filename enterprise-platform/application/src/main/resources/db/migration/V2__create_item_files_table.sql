CREATE TABLE item_files (
  id           BIGSERIAL     PRIMARY KEY,
  item_id      BIGINT        NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  original_name VARCHAR(255) NOT NULL,
  s3_key       VARCHAR(512)  NOT NULL UNIQUE,
  content_type VARCHAR(127)  NOT NULL,
  size_bytes   BIGINT        NOT NULL,
  uploaded_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_item_files_item_id ON item_files (item_id);
