INSERT INTO resources (name, http_method, path, description, requires_auth) VALUES

    -- Autenticación pública
    ('AUTH_REGISTER',         'POST',   '/api/v1/auth/register',          'Registro de nueva cuenta',                         FALSE),
    ('AUTH_KDF_PARAMS',       'GET',    '/api/v1/auth/kdf-params',        'Parámetros KDF del usuario para derivar clave',    FALSE),
    ('AUTH_LOGIN',            'POST',   '/api/v1/auth/login',             'Login con hash de contraseña maestra',             FALSE),
    ('AUTH_REFRESH',          'POST',   '/api/v1/auth/refresh',           'Renovar access token con refresh token',           FALSE),
    ('AUTH_RECOVER_ACCOUNT',  'POST',   '/api/v1/auth/recovery/recover',  'Iniciar recuperación de cuenta vía TOTP',          FALSE),

    -- Autenticación protegida
    ('AUTH_LOGOUT',           'POST',   '/api/v1/auth/logout',            'Cerrar sesión actual',                             TRUE),
    ('AUTH_SESSIONS_LIST',    'GET',    '/api/v1/auth/sessions',          'Listar sesiones activas del usuario',              TRUE),
    ('AUTH_SESSION_REVOKE',   'DELETE', '/api/v1/auth/sessions/{id}',     'Revocar una sesión específica',                    TRUE),

    -- 2FA / TOTP
    ('TOTP_SETUP',            'POST',   '/api/v1/auth/2fa/setup',         'Iniciar configuración de 2FA, devuelve QR',        TRUE),
    ('TOTP_VERIFY',           'POST',   '/api/v1/auth/2fa/verify',        'Confirmar código TOTP para activar 2FA',           TRUE),
    ('TOTP_DISABLE',          'DELETE', '/api/v1/auth/2fa',               'Desactivar 2FA de la cuenta',                      TRUE),

    -- Recuperación (requiere feature flag activo en servidor)
    ('RECOVERY_SETUP',        'POST',   '/api/v1/auth/recovery/setup',    'Configurar clave de recuperación cifrada',          TRUE),
    ('RECOVERY_DISABLE',      'DELETE', '/api/v1/auth/recovery',          'Desactivar recuperación de cuenta',                 TRUE),

    -- Bóveda
    ('VAULT_LIST',            'GET',    '/api/v1/vault',                  'Listar ítems del vault del usuario',               TRUE),
    ('VAULT_CREATE',          'POST',   '/api/v1/vault',                  'Crear nuevo ítem cifrado en el vault',             TRUE),
    ('VAULT_GET',             'GET',    '/api/v1/vault/{id}',             'Obtener un ítem cifrado por ID',                   TRUE),
    ('VAULT_UPDATE',          'PUT',    '/api/v1/vault/{id}',             'Actualizar ítem cifrado',                          TRUE),
    ('VAULT_DELETE',          'DELETE', '/api/v1/vault/{id}',             'Mover ítem a papelera',                            TRUE),
    ('VAULT_RESTORE',         'POST',   '/api/v1/vault/{id}/restore',     'Restaurar ítem desde papelera',                    TRUE),
    ('VAULT_PURGE',           'DELETE', '/api/v1/vault/{id}/purge',       'Eliminar ítem permanentemente',                    TRUE),
    ('VAULT_TRASH',           'GET',    '/api/v1/vault/trash',            'Listar ítems en papelera',                         TRUE),
    ('VAULT_EMPTY_TRASH',     'DELETE', '/api/v1/vault/trash',            'Vaciar papelera permanentemente',                  TRUE),

    -- Carpetas
    ('FOLDER_LIST',           'GET',    '/api/v1/folders',                'Listar carpetas del usuario',                      TRUE),
    ('FOLDER_CREATE',         'POST',   '/api/v1/folders',                'Crear nueva carpeta cifrada',                      TRUE),
    ('FOLDER_UPDATE',         'PUT',    '/api/v1/folders/{id}',           'Actualizar nombre cifrado de carpeta',             TRUE),
    ('FOLDER_DELETE',         'DELETE', '/api/v1/folders/{id}',           'Eliminar carpeta',                                 TRUE),

    -- Sistema
    ('HEALTH',                'GET',    '/api/v1/health',                 'Estado del servidor',                              FALSE);


-- Asignar todos los recursos protegidos al rol USER
INSERT INTO role_resources (role_id, resource_id)
SELECT r.id, res.id
FROM roles r, resources res
WHERE r.name = 'USER'
  AND res.requires_auth = TRUE;

-- ADMIN hereda todo lo del USER más acceso completo
INSERT INTO role_resources (role_id, resource_id)
SELECT r.id, res.id
FROM roles r, resources res
WHERE r.name = 'ADMIN';
