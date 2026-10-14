# Despliegue del backend en el servidor propio (FT00041)

Runbook reproducible para desplegar **backend + PostgreSQL/pgvector + Nginx** en el
servidor propio (Intel N100, 8 GB) con **Docker Compose** y **HTTPS**, dejar la
**ingesta diaria con segunda pasada** en marcha y mantener los **secretos solo por
entorno** (nunca versionados).

Decisión durable en el ADR `0030-despliegue-servidor-propio` (harness privado).

## Qué hay aquí

| Fichero | Para qué |
| --- | --- |
| `deploy/docker-compose.prod.yml` | Compose de producción: `postgres` (loopback), `backend` (sin puerto) y `nginx` (80/443). |
| `deploy/nginx/templates/datopublico.conf.template` | Config de Nginx: TLS, redirect 80→443 y proxy con `X-Forwarded-*`. |
| `deploy/.env.production.example` | Plantilla de entorno (**solo placeholders**). Cópiala a `deploy/.env.production`. |
| `deploy/verify-deploy.sh` | Verificación offline **opt-in** (Compose + Nginx + secretos); no la ejecuta el gate. |
| `backend/Dockerfile` | Imagen multi-stage (`installDist -PskipClient`, sin Android SDK, sin secretos ni modelo). |
| `.dockerignore` | Contexto de build pequeño y sin secretos. |

## Arquitectura del despliegue

- **`nginx`** es la **única superficie publicada** (80/443): termina TLS y hace
  proxy a `backend:8080` por la red interna de Compose.
- **`backend`** **no** publica puerto al host: solo Nginx lo alcanza. Confía en las
  cabeceras `X-Forwarded-*` de Nginx (`TRUST_PROXY_HEADERS=true`) para que el
  *rate limiting* por IP (FT00030) vea la **IP del cliente**.
- **`postgres`** se publica **solo en loopback** (`127.0.0.1:${POSTGRES_PORT}`) para
  que el host aplique Flyway; los datos persisten en el volumen `pgdata`.
- La **ingesta** es un **scheduler interno por corrutina** (09:30 + 18:00,
  `Europe/Madrid`; ADR 0004), **sin cron externo**; el contenedor se reinicia solo
  (`restart: unless-stopped`).

## Requisitos

- **Docker Engine** + plugin **Docker Compose** en el servidor.
- Un **dominio** con registro DNS (A/AAAA) apuntando a la IP del servidor y los
  puertos **80** y **443** accesibles desde Internet (HTTP-01 de Let's Encrypt).
- **certbot** instalado en el host para emitir el certificado TLS.

## Pasos

Todos los comandos se ejecutan desde la **raíz del repositorio** en el servidor.

### 1. Código y modelo

```sh
git clone https://github.com/AviferDev/DatoPublico.git && cd DatoPublico

# Modelo E5 (~118 MB, no versionado): aprovisiónalo en el host ANTES de levantar.
backend/tools/download-embedding-model.sh
# → backend/models/multilingual-e5-small/{model.onnx,tokenizer.json}
```

### 2. Entorno de producción

```sh
cp deploy/.env.production.example deploy/.env.production
# Rellena los valores reales (dominio, POSTGRES_PASSWORD, OPENCODE_API_KEY, …).
chmod 600 deploy/.env.production          # permisos restringidos
```

`deploy/.env.production` está **ignorado por git**: los secretos nunca se
versionan. `POSTGRES_PASSWORD` y `OPENCODE_API_KEY` son **obligatorios**
(fail-fast): si faltan, el stack no arranca con un mensaje claro.

### 3. Certificado TLS (manual)

Let's Encrypt HTTP-01 necesita el puerto 80 libre. Con el stack parado:

```sh
DOMAIN="$(grep -E '^DOMAIN=' deploy/.env.production | cut -d= -f2-)"
sudo certbot certonly --standalone -d "$DOMAIN" --agree-tos -m tu@correo.example
# → /etc/letsencrypt/live/$DOMAIN/{fullchain.pem,privkey.pem}
```

El compose monta `/etc/letsencrypt` (solo lectura) en Nginx. Para **renovar**,
vuelve a parar Nginx, renueva y arráncalo:

```sh
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production stop nginx
sudo certbot renew
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production start nginx
```

### 4. Base de datos y migraciones

```sh
# Solo PostgreSQL (loopback) para aplicar Flyway desde el host.
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production up -d postgres

# Flyway lee `POSTGRES_*` del entorno; cárgalo desde la plantilla real.
set -a; . deploy/.env.production; set +a
./gradlew :backend:flywayMigrate
./gradlew :backend:flywayInfo        # V1…V5 en Success
```

### 5. Levantar el stack completo

```sh
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production up -d
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production ps
# postgres / backend / nginx: healthy (o up)
```

La primera vez, `docker compose up -d` construye la imagen del backend
(`docker build`), lo que **requiere red** (imagen base + dependencias de Gradle);
queda **fuera del gate** y de la CI.

### 6. Comprobación HTTPS

```sh
curl -fsS "https://$DOMAIN/health"                 # {"status":"ok"}
curl -sI "http://$DOMAIN/health" | head -n 1       # HTTP/1.1 301 … (redirect a HTTPS)
```

Nginx termina TLS, redirige HTTP→HTTPS y reenvía `X-Forwarded-*` al backend.

### 7. Observar la ingesta

```sh
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production logs -f backend \
  | grep -E '"event":"ingestion\.(run|missed)'
# 09:30 y la segunda pasada de 18:00 → event=ingestion.run.completed
# reejecutar la misma fecha NO duplica (upsert por id)
```

### 8. Rate limiting tras el proxy

Con `TRUST_PROXY_HEADERS=true`, dos clientes con IPs distintas tienen cupos
**independientes** (`X-Forwarded-For` de Nginx). Con el flag **apagado** el plugin
no se instala y el rate limiting vería la IP del proxy.

### 9. Persistencia

```sh
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production restart
# Los datos siguen: el volumen `pgdata` persiste entre reinicios.
```

### 10. Rollback

```sh
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production down
# `down -v` NO se usa en producción: borraría el volumen `pgdata` (los datos).
```

## Verificación offline (opt-in)

```sh
./deploy/verify-deploy.sh
```

Comprueba estáticamente que no hay secretos versionados y que
`deploy/.env.production` está ignorado; con Docker, valida `docker compose config`
y (si el daemon responde) hace *lint* de la configuración de Nginx. Si falta
Docker, omite con un mensaje claro. **No** la ejecuta el gate estándar ni la CI.

## Reglas de seguridad (MUST)

- **Secretos solo por entorno**, nunca versionados: `deploy/.env.production` está
  ignorado por git y la plantilla solo lleva *placeholders*.
- **Fail-fast** ante secreto ausente (`POSTGRES_PASSWORD`, `OPENCODE_API_KEY`).
- **PostgreSQL de producción solo en loopback**; el **backend no publica puerto**.
- **HTTPS obligatorio**: Nginx es la única entrada y redirige HTTP→HTTPS.
- **`TRUST_PROXY_HEADERS` es opt-in**: solo se activa detrás del proxy (el backend
  nunca publica puerto).
- El **gate estándar no arranca servidores ni Docker**: `docker build`,
  `docker compose` y `flywayMigrate` son explícitos/opt-in.

## Fuera de alcance (features posteriores)

- **Backups y observabilidad de producción** (retención, alertas externas,
  métricas): FT00042.
- **Certificados/DNS gestionados por código** (Terraform, cert-manager): el
  certificado se emite **a mano**.
- **Cron externo**: la ingesta es un scheduler interno (ADR 0004).
