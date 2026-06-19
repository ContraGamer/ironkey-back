CREATE TABLE vault_items (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id       UUID        REFERENCES folders(id) ON DELETE SET NULL,

    -- Blob JSON cifrado en cliente: {name, url, username, password, notes, tags}
    encrypted_data  TEXT        NOT NULL,
    iv              VARCHAR(255) NOT NULL,

    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vault_items_user_id   ON vault_items(user_id);
CREATE INDEX idx_vault_items_folder_id ON vault_items(folder_id);
CREATE INDEX idx_vault_items_deleted   ON vault_items(user_id, deleted_at);
