# PostgreSQL — Configuración para IronKey Backend

Antes de levantar el backend de IronKey por primera vez, necesitas tener una instancia de
PostgreSQL corriendo con una base de datos y un usuario dedicado. Esta guía cubre los pasos
desde cero.

---

## Requisitos

- PostgreSQL 15 o superior
- Acceso al servidor con permisos de superusuario en la DB

---

## 1. Instalación de PostgreSQL

### Ubuntu / Debian (VPS recomendado)

```bash
sudo apt update
sudo apt install -y postgresql postgresql-contrib

# Verificar que está corriendo
sudo systemctl status postgresql
sudo systemctl enable postgresql
```

### macOS (desarrollo local)

```bash
brew install postgresql@16
brew services start postgresql@16
```

### Docker (desarrollo local alternativo)

```bash
docker run -d \
  --name ironkey-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=supersecret \
  -p 5432:5432 \
  postgres:16-alpine
```

---

## 2. Crear usuario y base de datos

Conéctate como superusuario de PostgreSQL:

```bash
# En Linux/VPS
sudo -u postgres psql

# En macOS
psql -U postgres
```

Ejecuta los siguientes comandos dentro de `psql`:

```sql
-- Crear usuario dedicado para IronKey (cambia la contraseña)
CREATE USER ironkey_user WITH PASSWORD 'tu_contraseña_segura_aqui';

-- Crear la base de datos
CREATE DATABASE ironkey_db
    OWNER ironkey_user
    ENCODING 'UTF8'
    LC_COLLATE 'en_US.UTF-8'
    LC_CTYPE 'en_US.UTF-8'
    TEMPLATE template0;

-- Dar todos los privilegios sobre la base de datos
GRANT ALL PRIVILEGES ON DATABASE ironkey_db TO ironkey_user;

-- Conectarse a la DB para dar permisos sobre el schema
\c ironkey_db

GRANT ALL ON SCHEMA public TO ironkey_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ironkey_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ironkey_user;

-- Que los objetos futuros (creados por Flyway) también tengan permisos
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL ON TABLES TO ironkey_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL ON SEQUENCES TO ironkey_user;

-- Salir
\q
```

---

## 3. Verificar la conexión

```bash
psql -h localhost -U ironkey_user -d ironkey_db -c "SELECT version();"
```

Si responde con la versión de PostgreSQL, la configuración es correcta.

---

## 4. Variables de entorno requeridas

El backend de IronKey no arranca si estas variables no están definidas. Créalas en el servidor
antes de levantar la aplicación.

| Variable | Descripción | Default | Ejemplo |
|----------|-------------|---------|---------|
| `DB_HOST` | Host de PostgreSQL | — | `localhost` o IP del servidor |
| `DB_PORT` | Puerto de PostgreSQL | `5432` | `5432` |
| `DB_NAME` | Nombre de la base de datos | — | `ironkey_db` |
| `DB_USER` | Usuario de la DB | — | `ironkey_user` |
| `DB_PASS` | Contraseña del usuario | — | `tu_contraseña_segura` |
| `JWT_SECRET` | Secreto para firmar tokens JWT (mínimo 64 chars) | — | ver sección 5 |
| `ENCRYPTION_PEPPER` | Pepper adicional para hashing (mínimo 32 chars) | — | ver sección 5 |
| `IRONKEY_RECOVERY_ENABLED` | Activa recuperación de cuenta vía TOTP | `false` | `false` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para CORS (separados por coma) | — | `https://tudominio.com` |
| `SERVER_PORT` | Puerto en el que escucha el backend | `8080` | `8080` |

> Las variables sin default son **obligatorias** — la app no arranca si faltan.

### En Linux/VPS — archivo `.env` para Docker Compose

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ironkey_db
DB_USER=ironkey_user
DB_PASS=tu_contraseña_segura_aqui

JWT_SECRET=cambia_esto_por_un_string_muy_largo_y_aleatorio_de_al_menos_64_caracteres
ENCRYPTION_PEPPER=otro_string_aleatorio_de_al_menos_32_caracteres

IRONKEY_RECOVERY_ENABLED=false

CORS_ALLOWED_ORIGINS=https://tudominio.com
```

### En Linux/VPS — variables de sistema (alternativa)

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=ironkey_db
export DB_USER=ironkey_user
export DB_PASS=tu_contraseña_segura_aqui
export JWT_SECRET=...
export ENCRYPTION_PEPPER=...
export IRONKEY_RECOVERY_ENABLED=false
export CORS_ALLOWED_ORIGINS=https://tudominio.com
```

---

## 5. Generar secretos seguros

Nunca uses valores predecibles para `JWT_SECRET` ni `ENCRYPTION_PEPPER`. Genera valores aleatorios:

```bash
# Generar JWT_SECRET (64 bytes en hex = 128 caracteres)
openssl rand -hex 64

# Generar ENCRYPTION_PEPPER (32 bytes en hex = 64 caracteres)
openssl rand -hex 32
```

Guarda estos valores en un lugar seguro (gestor de secretos, vault, archivo .env fuera del repo).
**Nunca los subas al repositorio.**

---

## 6. Configuración de PostgreSQL para conexiones remotas (VPS)

Si el backend y la DB corren en servidores distintos, edita la configuración de PostgreSQL:

```bash
# Encontrar el archivo de configuración
sudo -u postgres psql -c "SHOW config_file;"

# Editar postgresql.conf
sudo nano /etc/postgresql/16/main/postgresql.conf
```

Busca y cambia:
```
listen_addresses = '*'    # o la IP específica del servidor backend
```

Edita `pg_hba.conf` para permitir la conexión:
```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf
```

Agrega al final (reemplaza la IP con la del servidor backend):
```
host    ironkey_db    ironkey_user    IP_DEL_BACKEND/32    scram-sha-256
```

Reinicia PostgreSQL:
```bash
sudo systemctl restart postgresql
```

> Si back y DB corren en el mismo servidor (caso más común en VPS pequeño), no necesitas
> este paso — la conexión es local por `localhost`.

---

## 7. Flyway — migraciones automáticas

Al arrancar el backend por primera vez, Flyway crea automáticamente todas las tablas
ejecutando los scripts SQL en orden:

```
V1__create_users_table.sql        → tabla users (KDF params, vault key cifrada, TOTP, recovery, preferencias)
V2__create_folders_table.sql      → tabla folders (nombre cifrado en cliente)
V3__create_vault_items_table.sql  → tabla vault_items + índices (blob cifrado + papelera)
V4__create_refresh_tokens_table.sql → tabla refresh_tokens (sesiones con device info)
V5__create_roles_tables.sql       → tablas roles + user_roles
V6__create_resources_tables.sql   → tablas resources + role_resources
V7__seed_roles.sql                → roles iniciales: USER y ADMIN
V8__seed_resources.sql            → 27 endpoints registrados + asignación a roles
V9__add_user_preferences.sql      → columnas require_reprompt y vault_timeout_minutes en users
```

No necesitas ejecutar ningún SQL manualmente. Solo necesitas que la base de datos y el
usuario existan (pasos 2 y 3).

Si una migración falla, el backend no arranca y en los logs verás exactamente qué falló.

> **Nota:** Flyway lleva un registro de las migraciones aplicadas en la tabla `flyway_schema_history`.
> Si necesitas ver qué versiones están aplicadas en una instancia:
> ```sql
> SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;
> ```

---

## 8. Verificar que todo está listo

Antes de arrancar el backend, confirma:

```bash
# 1. PostgreSQL corriendo
sudo systemctl status postgresql

# 2. Conexión con el usuario de ironkey
psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "\dt"

# 3. Variables de entorno definidas
echo $DB_HOST $DB_NAME $JWT_SECRET
```

Si el paso 2 conecta y el paso 3 muestra las variables, el backend puede arrancar.

---

## 9. Firewall (VPS)

PostgreSQL no debe ser accesible desde internet. Solo el backend debe conectarse a él.

```bash
# Permitir PostgreSQL solo desde localhost (si back y DB están en el mismo servidor)
sudo ufw deny 5432

# O solo desde la IP del backend (si están en servidores distintos)
sudo ufw allow from IP_DEL_BACKEND to any port 5432
```

El puerto 5432 nunca debe estar abierto al público.
