CREATE TABLE resources (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL UNIQUE,
    http_method   VARCHAR(10)  NOT NULL,
    path          VARCHAR(255) NOT NULL,
    description   VARCHAR(255),
    requires_auth BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE role_resources (
    role_id     UUID NOT NULL REFERENCES roles(id)     ON DELETE CASCADE,
    resource_id UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, resource_id)
);
