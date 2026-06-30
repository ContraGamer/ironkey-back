# IronKey — Backend 🔑

Backend del gestor de contraseñas self-hosted IronKey. API REST construida con Java 21 y Spring Boot 4, diseñada con un modelo **zero-knowledge puro**: el servidor nunca puede descifrar las contraseñas almacenadas por los usuarios.

---

## ¿Qué es IronKey?

IronKey es un gestor de contraseñas self-hosted similar a Bitwarden, pensado para quienes quieren control total de sus datos sin depender de servicios de terceros. La aplicación se despliega en tu propia VPS y consta de tres piezas:

| Pieza | Repositorio | Estado |
|-------|-------------|--------|
| **ironkey-back** (este repo) | API REST — Java 21 + Spring Boot 4 | ✅ Completo |
| **ironkey-front** | SPA — React + Vite | 🔄 En desarrollo |
| **ironkey-extension** | Extensión para Chrome/Brave/Firefox | 📋 Planificado |

---

## Características

- **Zero-knowledge**: el servidor almacena únicamente blobs cifrados. Las claves de descifrado nunca salen del cliente.
- **Cifrado en cliente**: AES-256-GCM. El JSON con nombre, URL, usuario, contraseña y notas de cada credencial se cifra antes de enviarse.
- **KDF con Argon2id**: la contraseña maestra se deriva localmente con Argon2id antes de autenticar.
- **2FA con TOTP**: compatible con Google Authenticator, Aegis y cualquier app TOTP estándar.
- **Recuperación de cuenta**: opcional vía código de recuperación (activable con feature flag). El cliente genera el código, lo usa para cifrar la vault key, y el servidor solo guarda el hash SHA-256. Ni el código ni la vault key descifrada pasan por el servidor. Si está desactivado, la seguridad es zero-knowledge puro sin recuperación posible.
- **Papelera con restauración**: las credenciales eliminadas van a la papelera y pueden recuperarse antes del purge definitivo.
- **Gestión de sesiones**: múltiples sesiones con revocación individual o global. Refresh tokens rotativos opacos.
- **Carpetas cifradas**: el nombre de la carpeta también se cifra en el cliente.
- **Preferencias de usuario**: reprompt de contraseña maestra y timeout de sesión configurables por usuario.
- **Despliegue con Docker**: un solo `docker compose up -d` levanta el back, la DB y nginx con HTTPS.

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1 |
| Seguridad | Spring Security 7 + JWT (JJWT 0.12) |
| Base de datos | PostgreSQL 16 |
| Migraciones | Flyway |
| 2FA | dev.samstevens.totp + ZXing (QR) |
| Build | Gradle 9.5 |
| Contenedores | Docker + Docker Compose |
| Proxy | Nginx + Certbot (Let's Encrypt) |
| CI/CD | Drone CI |

---

## Modelo de cifrado

```
CLIENTE                                         SERVIDOR
──────────────────────────────────────────      ──────────────────────────
master_password
    │
    │ Argon2id  (parámetros KDF del servidor)
    ▼
master_derived_key
    ├──► encryption_key ──► AES-256-GCM ──► cifra vault_key
    └──► master_password_hash ──────────────────────────────► BCrypt → DB

vault_key ──AES-256-GCM──► protected_symmetric_key ─────────────────────► DB
vault_key ──AES-256-GCM──► recovery_protected_key ──────────────────────► DB (opcional)

Credenciales:
  cliente cifra → { name, url, username, password, notes, tags }
  servidor guarda → encrypted_data (blob) + iv
  servidor NUNCA descifra
```

---

## Requisitos previos

- **Docker** y **Docker Compose** (para producción o dev local con compose)
- **Java 21** y **Gradle 9.5** (solo para desarrollo sin Docker)
- **PostgreSQL 16+** accesible (o usar el compose de dev incluido)

---

## Inicio rápido — Desarrollo local

### 1. Clonar el repositorio y entrar a la rama de desarrollo

```bash
git clone git@github.com:ContraGamer/ironkey-back.git
cd ironkey-back
git checkout develop
```

### 2. Levantar PostgreSQL con Docker

```bash
docker compose -f docker-compose.dev.yml up -d
```

Esto levanta PostgreSQL en `localhost:5432` con:
- Base de datos: `ironkey_db`
- Usuario: `ironkey_user`
- Contraseña: `dev_password`

### 3. Arrancar el backend

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Al arrancar, **Flyway ejecuta automáticamente las 11 migraciones** y crea todo el esquema. No necesitas ejecutar ningún SQL manualmente.

### 4. Verificar que funciona

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","application":"ironkey","version":"0.0.1-SNAPSHOT","timestamp":"..."}
```

---

## Configuración de producción

### Variables de entorno requeridas

Copia `.env.example` a `.env` y completa todos los valores:

```bash
cp .env.example .env
```

| Variable | Descripción | Default | Obligatoria |
|----------|-------------|---------|-------------|
| `DB_HOST` | Host de PostgreSQL | — | ✅ |
| `DB_PORT` | Puerto de PostgreSQL | `5432` | — |
| `DB_NAME` | Nombre de la base de datos | — | ✅ |
| `DB_USER` | Usuario de la DB | — | ✅ |
| `DB_PASS` | Contraseña de la DB | — | ✅ |
| `JWT_SECRET` | Secreto HMAC para JWT (mín. 64 caracteres) | — | ✅ |
| `ENCRYPTION_PEPPER` | Pepper adicional para hashing (mín. 32 caracteres) | — | ✅ |
| `CORS_ALLOWED_ORIGINS` | Orígenes CORS permitidos, separados por coma | — | ✅ |
| `IRONKEY_RECOVERY_ENABLED` | Activa recuperación de cuenta vía recovery code | `false` | — |
| `SERVER_PORT` | Puerto del backend | `8080` | — |

> La app **no arranca** si alguna variable obligatoria no está definida.

### Generar secretos seguros

```bash
# JWT_SECRET (mínimo 64 chars)
openssl rand -hex 64

# ENCRYPTION_PEPPER (mínimo 32 chars)
openssl rand -hex 32
```

### Configurar la base de datos

Sigue la guía completa en [`docs/POSTGRESQL_SETUP.md`](docs/POSTGRESQL_SETUP.md). Resume en:

```sql
CREATE USER ironkey_user WITH PASSWORD 'tu_contraseña';
CREATE DATABASE ironkey_db OWNER ironkey_user ENCODING 'UTF8' TEMPLATE template0;
GRANT ALL PRIVILEGES ON DATABASE ironkey_db TO ironkey_user;
\c ironkey_db
GRANT ALL ON SCHEMA public TO ironkey_user;
```

---

## Despliegue en VPS con Docker Compose

### Primera vez — Bootstrap SSL

Antes de levantar nginx con HTTPS necesitas el certificado. Sigue el proceso en dos fases:

```bash
# Fase 1: levantar solo con HTTP para que Certbot valide el dominio
#   Edita nginx/nginx.conf y comenta el bloque server { listen 443... }
docker compose up -d nginx

# Obtener certificado
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v ironkey-back_certbot_webroot:/var/www/certbot \
  certbot/certbot certonly --webroot \
  -w /var/www/certbot \
  -d tudominio.com \
  --email tuemail@ejemplo.com \
  --agree-tos

# Fase 2: habilitar HTTPS en nginx.conf y levantar todo
docker compose up -d
```

### Arranque normal (después del bootstrap)

```bash
docker compose up -d
```

Flyway ejecuta las migraciones automáticamente al arrancar el backend.

### Ver logs

```bash
docker compose logs -f ironkey-back
docker compose logs -f postgres
```

---

## API — Endpoints disponibles

### Públicos (sin autenticación)

```
POST   /api/v1/auth/register           Crear cuenta nueva
GET    /api/v1/auth/kdf-params         Parámetros KDF para derivar la clave en el cliente
POST   /api/v1/auth/login              Autenticar (devuelve tokens + vault key cifrada)
POST   /api/v1/auth/refresh            Renovar access token con refresh token
GET    /api/v1/auth/recovery/data      Obtener vault key de recuperación + KDF params (si está habilitado)
POST   /api/v1/auth/recovery/recover   Recuperar cuenta con recovery code (si está habilitado)
GET    /api/v1/health                  Estado del servidor
```

### Protegidos (requieren `Authorization: Bearer <token>`)

```
# Sesiones
POST   /api/v1/auth/logout             Cerrar sesión actual
GET    /api/v1/auth/sessions           Listar sesiones activas
DELETE /api/v1/auth/sessions/{id}      Revocar sesión específica

# 2FA
POST   /api/v1/auth/2fa/setup          Iniciar configuración (devuelve QR en base64)
POST   /api/v1/auth/2fa/verify         Confirmar código y activar 2FA
DELETE /api/v1/auth/2fa                Desactivar 2FA

# Recuperación de cuenta (requiere IRONKEY_RECOVERY_ENABLED=true)
POST   /api/v1/auth/recovery/setup     Configurar recovery code (requiere 2FA activo)
DELETE /api/v1/auth/recovery           Desactivar recuperación (limpia hash + vault key cifrada)

# Preferencias de usuario
GET    /api/v1/settings                Leer preferencias (reprompt, timeout)
PUT    /api/v1/settings                Actualizar preferencias

# Bóveda
GET    /api/v1/vault                   Listar credenciales activas
POST   /api/v1/vault                   Crear credencial cifrada
GET    /api/v1/vault/{id}              Obtener credencial
PUT    /api/v1/vault/{id}              Actualizar credencial
DELETE /api/v1/vault/{id}              Mover a papelera (soft delete)
GET    /api/v1/vault/trash             Listar papelera
DELETE /api/v1/vault/trash             Vaciar papelera
POST   /api/v1/vault/{id}/restore      Restaurar desde papelera
DELETE /api/v1/vault/{id}/purge        Eliminar permanentemente

# Carpetas
GET    /api/v1/folders                 Listar carpetas
POST   /api/v1/folders                 Crear carpeta (nombre cifrado)
PUT    /api/v1/folders/{id}            Actualizar carpeta
DELETE /api/v1/folders/{id}            Eliminar carpeta (ítems pasan al vault raíz)
```

### Formato de respuesta de error

Todos los errores devuelven JSON con la misma estructura:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Se requiere código 2FA",
  "code": "TOTP_REQUIRED",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-06-19T10:00:00Z"
}
```

| Campo | Siempre presente | Descripción |
|-------|-----------------|-------------|
| `status` | ✅ | Código HTTP |
| `error` | ✅ | Texto HTTP estándar del código |
| `message` | ✅ | Mensaje legible para el usuario |
| `path` | ✅ | Endpoint que generó el error |
| `timestamp` | ✅ | ISO-8601 UTC |
| `code` | Solo en errores semánticos | Identificador estable para lógica de cliente (ver tabla) |
| `fieldErrors` | Solo en errores de validación (400) | Mapa campo → mensaje |

#### Códigos semánticos (`code`)

| Valor | HTTP | Cuándo se devuelve |
|-------|------|--------------------|
| `TOTP_REQUIRED` | 401 | Login exitoso pero el usuario tiene 2FA activo — el cliente debe pedir el código TOTP |

---

## Flujo de autenticación (para integradores)

```
1. GET  /auth/kdf-params?email=user@ejemplo.com
        ← { kdfType, kdfIterations, kdfMemory, kdfParallelism, kdfSalt }

2. [CLIENTE] Argon2id(masterPassword, kdfSalt, params) → masterKey
   [CLIENTE] Argon2id(masterKey, masterPassword, 1 iter) → masterPasswordHash

3. POST /auth/login  { email, masterPasswordHash, totpCode? }
        ← { accessToken, refreshToken, protectedSymmetricKey,
            protectedSymmetricKeyIv, requireReprompt, vaultTimeoutMinutes }

4. [CLIENTE] AES-256-GCM.decrypt(protectedSymmetricKey, masterKey) → vaultKey
   [CLIENTE] vaultKey queda en MEMORIA — nunca en localStorage

5. GET  /vault  (con Bearer token)
        ← [{ encryptedData, iv, folderId, ... }]

6. [CLIENTE] AES-256-GCM.decrypt(encryptedData, vaultKey) → JSON con credenciales
```

---

## Flujo de recuperación de cuenta (para integradores)

Requiere `IRONKEY_RECOVERY_ENABLED=true`. El recovery code se genera y gestiona íntegramente en el cliente — el servidor solo almacena su hash SHA-256.

### Configurar la recuperación (una vez, con 2FA activo)

```
1. [CLIENTE] Genera un recovery code aleatorio (mín. 20 chars, p.ej. 32 chars base32)

2. [CLIENTE] Argon2id(recoveryCode, kdfSalt, params) → recoveryKey
   [CLIENTE] AES-256-GCM.encrypt(vaultKey, recoveryKey) → recoveryProtectedKey + IV
   [CLIENTE] El usuario guarda el recovery code en papel/gestor offline

3. POST /auth/recovery/setup  { totpCode, recoveryProtectedKey, recoveryProtectedKeyIv, recoveryCode }
   ← 204 No Content
   [SERVIDOR] SHA-256(recoveryCode) → almacena hash + vault key cifrada
```

### Recuperar la cuenta (cuando se pierde el master password)

```
1. GET  /auth/recovery/data?email=user@ejemplo.com
        ← { recoveryProtectedKey, recoveryProtectedKeyIv,
            kdfType, kdfIterations, kdfMemory, kdfParallelism, kdfSalt }

2. [CLIENTE] Argon2id(recoveryCode, kdfSalt, params) → recoveryKey
   [CLIENTE] AES-256-GCM.decrypt(recoveryProtectedKey, recoveryKey) → vaultKey

3. [CLIENTE] Elige nuevo master password
   [CLIENTE] Argon2id(newPassword, kdfSalt, params) → newMasterKey
   [CLIENTE] AES-256-GCM.encrypt(vaultKey, newMasterKey) → newProtectedSymmetricKey + IV
   [CLIENTE] Argon2id(newMasterKey, newPassword, 1 iter) → newMasterPasswordHash

4. POST /auth/recovery/recover  { email, recoveryCode,
                                  newMasterPasswordHash,
                                  newProtectedSymmetricKey, newProtectedSymmetricKeyIv }
        ← { accessToken, refreshToken, protectedSymmetricKey, ... }
   [SERVIDOR] verifica SHA-256(recoveryCode) == hash almacenado
   [SERVIDOR] actualiza password hash + protected key + revoca todas las sesiones activas
```

> **Nota de seguridad**: el recovery code nunca queda almacenado en el servidor en texto claro. El servidor no puede descifrar la vault key en ningún momento del proceso.

---

## Migraciones de base de datos

Flyway aplica las migraciones en orden al arrancar. No se requiere intervención manual:

| Versión | Contenido |
|---------|-----------|
| V1 | Tabla `users` (KDF, vault key cifrada, TOTP, recovery, preferencias) |
| V2 | Tabla `folders` |
| V3 | Tabla `vault_items` + índices |
| V4 | Tabla `refresh_tokens` |
| V5 | Tablas `roles` + `user_roles` |
| V6 | Tablas `resources` + `role_resources` |
| V7 | Seed: roles USER y ADMIN |
| V8 | Seed: 27 endpoints + asignación a roles |
| V9 | Columnas `require_reprompt` y `vault_timeout_minutes` en `users` |
| V10 | Columnas `failed_login_attempts` y `locked_until` en `users` (account lockout) |
| V11 | Columna `recovery_code_hash` en `users` (verificación del recovery code) |

Para ver el estado de migraciones en una instancia activa:

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

---

## Ejecutar los tests

```bash
./gradlew test
```

Los tests usan H2 en memoria con compatibilidad PostgreSQL. No se necesita una instancia de PostgreSQL para correr los tests.

```bash
# Ver reporte HTML de tests
open build/reports/tests/test/index.html
```

---

## CI/CD con Drone

El pipeline en `.drone.yml` tiene tres pasos:

| Paso | Rama | Qué hace |
|------|------|----------|
| `build-and-test` | `develop` y `main` | Compila con `-x test` (tests pendientes de activar en CI) |
| `docker-build-and-push` | `develop` y `main` | Construye imagen Docker y la sube al registry |
| `deploy` | `develop` y `main` | Conecta al VPS por SSH y ejecuta `docker compose up` |

### Secrets requeridos en Drone

Configura estos secrets en el panel de Drone antes del primer deploy:

| Secret | Descripción |
|--------|-------------|
| `REGISTRY_USERNAME` | Usuario del registry Docker privado |
| `REGISTRY_PASSWORD` | Contraseña del registry Docker privado |
| `DEPLOY_HOST` | IP o dominio del VPS |
| `DEPLOY_USER` | Usuario SSH del VPS |
| `DEPLOY_SSH_KEY` | Clave privada SSH (contenido completo) |

> La URL del registry (`registry.contragamer.com`), la ruta de la imagen y el path de deploy en el VPS están hardcodeados en `.drone.yml` para evitar errores por secrets mal configurados.

### Flujo de trabajo con ramas

```bash
# Trabajo diario — en develop
git checkout develop
# ... cambios ...
git push origin develop   # dispara: build + test

# Release — merge a main
git checkout main
git merge develop
git push origin main      # dispara: build + test + push imagen + deploy
```

---

## Estructura del proyecto

```
ironkey-back/
├── src/
│   ├── main/
│   │   ├── java/com/lionfinance/ironkey/
│   │   │   ├── api/
│   │   │   │   ├── controller/     AuthController, VaultController, FolderController,
│   │   │   │   │                   SettingsController, HealthController
│   │   │   │   ├── dto/            auth/, vault/, settings/, common/
│   │   │   │   └── handler/        GlobalExceptionHandler
│   │   │   ├── domain/
│   │   │   │   ├── entity/         User, VaultItem, Folder, RefreshToken, Role, Resource
│   │   │   │   └── repository/     6 repositorios con queries custom
│   │   │   ├── exception/          IronKeyException y 8 subclases
│   │   │   ├── security/
│   │   │   │   ├── config/         SecurityConfig, TotpConfig
│   │   │   │   ├── filter/         JwtAuthenticationFilter
│   │   │   │   ├── jwt/            JwtService, JwtProperties
│   │   │   │   └── userdetails/    IronKeyUserDetails, IronKeyUserDetailsService
│   │   │   └── service/            AuthService, VaultService, FolderService, SettingsService
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/       V1 → V11 (Flyway)
│   └── test/                       59 tests (unit + @WebMvcTest)
├── nginx/
│   └── nginx.conf                  Reverse proxy con HTTPS y security headers
├── docs/
│   └── POSTGRESQL_SETUP.md         Guía completa de configuración de DB
├── .drone.yml                      Pipeline CI/CD
├── .env.example                    Plantilla de variables de entorno
├── docker-compose.yml              Producción: back + postgres + nginx
├── docker-compose.dev.yml          Desarrollo: solo postgres
└── Dockerfile                      Multi-stage build con JRE 21 Alpine
```

---

## Changelog

### 2026-06-30 — Bugs resueltos en ironkey-ui + campo `code` en errores

#### Backend: campo `code` en respuestas de error (`49b6a78`)

El cliente no tenía forma confiable de distinguir "credenciales inválidas" de "login correcto pero falta el código TOTP" — ambos devuelven HTTP 401. El frontend detectaba el caso por el texto del mensaje, frágil a cualquier cambio de wording.

- `ErrorResponse` — nuevo campo `code` (nullable, omitido si null vía `@JsonInclude(NON_NULL)`)
- `IronKeyException` — método `getCode()` que retorna `null` por defecto; subclases pueden sobreescribirlo
- `TotpRequiredException` — sobreescribe `getCode()` → `"TOTP_REQUIRED"`
- `GlobalExceptionHandler` — incluye `ex.getCode()` en la respuesta de cualquier `IronKeyException`

#### Frontend (ironkey-ui): 5 bugs resueltos

| Commit | Descripción |
|--------|-------------|
| `8597b11` | **IV y ciphertext como campos separados** — `encryptVaultItem` enviaba un string combinado `"iv:ciphertext"` como `encryptedData`; el backend requería `iv` como campo `@NotBlank` separado → 400 en todos los creates y updates. También corregida la lectura: `decryptVaultItem` ahora recibe `(encryptedData, iv)` por separado como los devuelve el servidor. |
| `235b1f7` | **Desactivar 2FA requería TOTP** — `DELETE /auth/2fa` se llamaba sin body; el backend requiere `{ totpCode }`. Añadido modal de confirmación en Settings que pide el código antes de ejecutar. |
| `d1e37cd` | **Editar un item eliminaba su carpeta** — `updateItem` no enviaba `folderId`; el backend interpreta `null` como "mover al vault raíz". Ahora se preserva la carpeta original del item. |
| `8670850` | **Recarga redirigía a `/login` en vez de `/unlock`** — cuando la `vaultKey` se pierde al recargar (por diseño zero-knowledge) pero el refresh token sigue válido, la pantalla correcta es `/unlock` (email pre-cargado, solo pide contraseña). |
| `4566c6d` | **Detección de TOTP por `code` en vez de string** — aprovecha el nuevo campo `code: "TOTP_REQUIRED"` del backend; elimina la condición frágil basada en el texto del mensaje. |

---

## Seguridad

- Las contraseñas del vault **nunca llegan al servidor en texto claro** ni cifradas con claves que el servidor conoce.
- El servidor almacena el `masterPasswordHash` con BCrypt adicional sobre la salida KDF del cliente.
- Los refresh tokens son valores opacos aleatorios (64 bytes) almacenados hasheados con SHA-256. No son JWTs.
- El puerto de PostgreSQL (5432) **nunca debe estar expuesto** a internet.
- HTTPS es obligatorio en producción. El nginx incluido está configurado con TLS 1.2/1.3 únicamente.

Para el análisis de seguridad completo del modelo de cifrado, ver [`docs/POSTGRESQL_SETUP.md`](docs/POSTGRESQL_SETUP.md).

---

## Licencia

Uso privado — Lion Finance.
