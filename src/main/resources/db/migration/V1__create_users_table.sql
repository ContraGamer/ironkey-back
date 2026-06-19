CREATE TABLE users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                       VARCHAR(255)    NOT NULL UNIQUE,

    -- Hash de la salida del KDF del cliente (nunca el master_password en claro)
    master_password_hash        VARCHAR(255)    NOT NULL,

    -- Parámetros KDF que el cliente necesita para derivar su clave
    kdf_type                    VARCHAR(20)     NOT NULL DEFAULT 'argon2id',
    kdf_iterations              INTEGER         NOT NULL DEFAULT 3,
    kdf_memory                  INTEGER         NOT NULL DEFAULT 65536,
    kdf_parallelism             INTEGER         NOT NULL DEFAULT 4,
    kdf_salt                    VARCHAR(255)    NOT NULL,

    -- Clave simétrica del vault cifrada con la master_derived_key del cliente
    protected_symmetric_key     TEXT            NOT NULL,
    protected_symmetric_key_iv  VARCHAR(255)    NOT NULL,

    -- 2FA / TOTP
    totp_secret                 VARCHAR(255),
    totp_enabled                BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Recuperación de cuenta (feature flag dependiente)
    recovery_enabled            BOOLEAN         NOT NULL DEFAULT FALSE,
    recovery_protected_key      TEXT,
    recovery_protected_key_iv   VARCHAR(255),

    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
