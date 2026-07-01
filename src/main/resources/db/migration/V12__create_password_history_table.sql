CREATE TABLE password_history (
    id              BIGSERIAL   PRIMARY KEY,
    vault_item_id   UUID        NOT NULL REFERENCES vault_items(id) ON DELETE CASCADE,
    encrypted_data  TEXT        NOT NULL,
    iv              VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_history_item ON password_history(vault_item_id, created_at DESC);
