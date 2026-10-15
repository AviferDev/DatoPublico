# Publicación de versiones (release)

Runbook para publicar DatoPublico desde **GitHub Actions**: construir y publicar la
**imagen del backend en GHCR** y desplegarla por **SSH** en el servidor propio
(`pull` + migraciones Flyway + `docker compose up -d` + health-check), sin
versionar **ningún** secreto y sin tocar el **gate** de desarrollo.

Decisión durable en el ADR `0034-release-github-actions` (harness privado). El
despliegue del servidor (Docker, Nginx, TLS, secretos del servidor) está en
[`deploy/README.md`](../deploy/README.md).

## Reglas

- **Disparo manual o por tag**: `workflow_dispatch` (botón, con parámetros) o
  `push` de un tag **`vMAJOR.MINOR.PATCH`**. **Nunca** hay despliegue automático
  en cada `push` a `main`.
- **Versionado por tag semver**: `0.x` es preproducción y `1.0.0` la primera
  versión pública. La versión vive en el **tag** (y en el tag de la imagen); no se
  duplica en ningún fichero.
- **Permisos mínimos**: `contents: read` salvo el job de la imagen, que añade
  `packages: write` para GHCR. En el **umbrella**, el job que invoca el workflow
  reutilizable del backend **también** declara `packages: write`: un workflow
  reutilizable solo puede *degradar* los permisos del *caller*, así que si el
  *umbrella* no lo concede, la publicación en GHCR se deniega (el disparo por tag
  directo no pasa por el *umbrella* y no se ve afectado).
- **La concurrencia no cancela despliegues** (`cancel-in-progress: false`): un
  despliegue en curso termina siempre.
- **El gate no cambia**: `./gradlew build -PskipClient`, `./gradlew test`,
  `./gradlew detekt` y `.harness/init.sh` siguen siendo offline (sin Docker, red ni
  BD) y **no** ejecutan ningún workflow de publicación.
- **Ningún secreto versionado**: solo *placeholders* en las plantillas; los
  secretos viven en GitHub (Secrets/Environments) o en el servidor
  (`deploy/.env.production`, ignorado por git).

## Workflows

| Fichero | Trigger | Qué hace |
| --- | --- | --- |
| `.github/workflows/release.yml` | `workflow_dispatch` | Umbrella: reparte la publicación entre los destinos (`targets`). En PT1 solo el backend. |
| `.github/workflows/release-backend.yml` | `workflow_call`, `workflow_dispatch`, `push: tags: v*` | Job `image` (Buildx → GHCR) y job `deploy` (SSH + `scripts/release/deploy-backend.sh`). |
| `.github/actions/setup-jdk-gradle` | — (acción compuesta) | JDK 21 (temurin) + Gradle con caché; la usan los workflows que compilan en el runner (Web/Android). |
| `scripts/release/deploy-backend.sh` | — (servidor) | Despliegue en el servidor: pull + Flyway + `up -d` + health-check, fail-fast. |

El **umbrella** solo referencia workflows que existen en el commit: cada parte
(`PT2` Web, `PT3` Android, `PT4` iOS) añade su job cuando su workflow aterriza.

## Inventario de secretos y variables

Se crean en **Settings → Secrets and variables → Actions**; pueden estar acotados a
un **Environment** (recomendado para `production`).

### Secretos

| Secreto | Para | Notas |
| --- | --- | --- |
| `SSH_HOST` | Backend (PT1), Web (PT2) | Host del servidor. |
| `SSH_USER` | Backend, Web | Usuario de despliegue. |
| `SSH_PRIVATE_KEY` | Backend, Web | Clave privada **solo** para desplegar. |
| `SSH_KNOWN_HOSTS` | Backend, Web | *Host key* fijada (evita MITM). |
| `ANDROID_KEYSTORE_BASE64` | Android (PT3) | Keystore release en base64. |
| `ANDROID_KEYSTORE_PASSWORD` | Android | — |
| `ANDROID_KEY_ALIAS` | Android | — |
| `ANDROID_KEY_PASSWORD` | Android | — |
| `PLAY_SERVICE_ACCOUNT_JSON` | Android | Clave JSON de la cuenta de servicio de Play. |
| `ASC_KEY_ID` | iOS (PT4) | App Store Connect API key id. |
| `ASC_ISSUER_ID` | iOS | Issuer id. |
| `ASC_PRIVATE_KEY` | iOS | Contenido del `.p8`. |
| `BUILD_CERTIFICATE_BASE64` | iOS | Certificado de distribución (`.p12` base64). |
| `P12_PASSWORD` | iOS | Contraseña del `.p12`. |
| `PROVISIONING_PROFILE_BASE64` | iOS | Perfil de aprovisionamiento (base64). |

### Variables (no secretas)

| Variable | Para | Valor previsto |
| --- | --- | --- |
| `SSH_PORT` | Backend, Web | `22` (si falta, se usa 22). |
| `DEPLOY_PATH` | Backend, Web | Ruta del checkout del repo en el servidor. |
| `WEB_ROOT` | Web (PT2) | Ruta del webroot estático de Nginx. |
| `PROD_API_BASE_URL` | Web (PT2) | `https://<dominio>`. |
| `ANDROID_PACKAGE_NAME` | Android (PT3) | `es.aviferdev.datopublico`. |
| `PLAY_TRACK` | Android | `internal`. |
| `APPLE_TEAM_ID`, `IOS_BUNDLE_ID`, `IOS_SCHEME`, `IOS_PROJECT` | iOS (PT4) | Datos de firma/archivo. |

Notas:

- **GHCR no necesita secreto**: usa el `GITHUB_TOKEN` con `packages: write`.
- Los **secretos del servidor** (`POSTGRES_PASSWORD`, `OPENCODE_API_KEY`…) **no**
  viajan a GitHub: permanecen en `deploy/.env.production` del servidor.
- La imagen se publica como `ghcr.io/<owner-en-minúsculas>/datopublico-backend`.

## Entornos (GitHub Environments)

| Entorno | Para | Configuración |
| --- | --- | --- |
| `production` | Backend, Web | **Required reviewers** (el dueño); secretos/variables `SSH_*`/`DEPLOY_PATH` acotables aquí. |
| `internal` | Android (Play internal), iOS (TestFlight) | Sin revisores; secretos de tienda. |

El job `deploy` del backend declara `environment: production`: la publicación se
detiene hasta que un revisor la aprueba. El backend **rechaza** cualquier entorno
distinto de `production` con un mensaje claro.

## Cómo publicar

### 1. Por tag (recomendado)

```sh
git checkout main && git pull
git tag -a v0.7.0 -m "DatoPublico 0.7.0"
git push origin v0.7.0
```

El tag `v*` dispara `release-backend.yml`: valida la versión, construye la imagen
(`backend/Dockerfile`), la publica en GHCR con los tags `0.7.0` y `latest` y
despliega en el servidor.

### 2. A mano (dispatch)

**Actions → Release → Run workflow** con:

| Entrada | Ejemplo | Descripción |
| --- | --- | --- |
| `targets` | `backend` | Destinos separados por comas (`backend`, `web`, `android`, `ios`) o `all`. |
| `version` | `0.7.1` | Versión sin la `v`; vacío = la del tag del ref. |
| `environment` | `production` | GitHub Environment (`production` para backend/Web, `internal` para tiendas). |

Equivalente por CLI:

```sh
gh workflow run release.yml -f targets=backend -f version=0.7.1 -f environment=production
```

### 3. Un solo destino directamente

```sh
gh workflow run release-backend.yml -f version=0.7.1 -f environment=production
```

## Qué hace el despliegue (en el servidor)

El job `deploy` se conecta por SSH y, en `DEPLOY_PATH`:

```sh
git fetch --tags --prune --force
git checkout --force "v${BACKEND_VERSION}"   # tag inmutable: despliegue idempotente
./scripts/release/deploy-backend.sh
```

`scripts/release/deploy-backend.sh` (fail-fast) hace:

1. `docker compose up -d postgres` y espera a `healthy` (publicado **solo en
   loopback**; los datos persisten en el volumen `pgdata`).
2. **Migraciones Flyway**: `./gradlew :backend:flywayMigrate` si el servidor tiene
   JDK; si no, el contenedor `flyway/flyway:11-alpine` contra `127.0.0.1`
   (`FLYWAY_MODE=auto|gradle|container` lo fuerza si hace falta). Flyway **nunca**
   se engancha al gate.
3. `docker compose pull backend` (imagen del tag publicado) + `docker compose up -d`
   (recrea el backend; `postgres` y `nginx` no se tocan).
4. Health-check: contenedor `healthy` + `GET https://$DOMAIN/health` → 200.

Si cualquiera de los pasos falla, el script sale con código ≠ 0, imprime
`docker compose ps`/logs y el run queda en **rojo**: el release **no** se marca como
bueno. Con `restart: unless-stopped`, el contenedor sano (anterior o nuevo) sigue
atendiendo.

## Verificar un despliegue

```sh
curl -fsS "https://$DOMAIN/health"                # {"status":"ok"}
curl -fsS "https://$DOMAIN/openapi.json" >/dev/null
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production ps
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production logs -f backend \
  | grep -E '"event":"ingestion\.(run|missed)'
```

En el runner (o en cualquier máquina) se puede comprobar que la imagen publicada es
la esperada:

```sh
docker pull ghcr.io/aviferdev/datopublico-backend:0.7.0
docker image inspect ghcr.io/aviferdev/datopublico-backend:0.7.0 --format '{{.Id}}'
```

## Rollback

El estado deseado es un **tag**, así que revertir es volver a desplegar el tag
anterior (nunca se edita el tag malo):

```sh
gh workflow run release-backend.yml -f version=0.6.1 -f environment=production
```

El workflow hace `git checkout` del tag, `docker compose pull` de esa imagen,
aplica Flyway (que no revierte migraciones: son **aditivas** por regla del
proyecto) y reinicia el backend. El `restart: unless-stopped` mantiene el servicio
en pie durante la operación.

> Nota: el rollback usa tags que **incluyan** `scripts/release/deploy-backend.sh`
> (a partir de `v0.7.0`). Para un tag anterior, despliega a mano con
> `deploy/README.md`.

## Despliegue manual / primer despliegue

La plantilla `deploy/.env.production.example` declara `BACKEND_IMAGE` y
`BACKEND_VERSION` (*placeholders*). En el servidor, añádelas a
`deploy/.env.production` y, para fijar una versión concreta:

```sh
export BACKEND_IMAGE=ghcr.io/aviferdev/datopublico-backend
export BACKEND_VERSION=0.7.0
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production pull backend
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production up -d
```

Sin `image` en GHCR, `docker compose up -d` sigue construyendo la imagen en el
propio servidor (el `build:` se conserva), como describía `deploy/README.md`.

## Verificación local (opt-in)

Sin cuentas ni servidor, el cambio se verifica en local con:

```sh
bash -n scripts/release/*.sh
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production.example config -q
./deploy/verify-deploy.sh
```

Y, con Docker, la imagen se puede construir igual que la construye el workflow:

```sh
docker build -f backend/Dockerfile -t datopublico-backend:test .
```

Nada de esto lo ejecuta el gate estándar (`./gradlew build`/`test`/`detekt` ni
`.harness/init.sh`).

## Estado por partes

- **PT1 (backend)**: implementado — imagen a GHCR + despliegue por SSH.
- **PT2 (Web)**, **PT3 (Android)** y **PT4 (iOS)**: pendientes de sus
  prerrequisitos (`dist-web`/FT00039, `dist-android`/FT00038, `dist-ios`/FT00040);
  añadirán su job al umbrella y su sección aquí. La herramienta de subida a tiendas
  (fastlane, acción de Play, etc.) se decide en el ADR `0034`.
