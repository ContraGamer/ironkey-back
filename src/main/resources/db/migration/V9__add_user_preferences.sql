ALTER TABLE users
    ADD COLUMN require_reprompt      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN vault_timeout_minutes INTEGER NOT NULL DEFAULT 15;

-- Registrar los nuevos endpoints en la tabla de recursos
INSERT INTO resources (name, http_method, path, description, requires_auth) VALUES
    ('SETTINGS_GET', 'GET', '/api/v1/settings', 'Obtener preferencias del usuario',    TRUE),
    ('SETTINGS_PUT', 'PUT', '/api/v1/settings', 'Actualizar preferencias del usuario',  TRUE);

-- Asignar los nuevos recursos al rol USER y ADMIN
INSERT INTO role_resources (role_id, resource_id)
SELECT r.id, res.id
FROM roles r, resources res
WHERE r.name IN ('USER', 'ADMIN')
  AND res.name IN ('SETTINGS_GET', 'SETTINGS_PUT');
