#!/usr/bin/env bash
#
# deploy-backend.sh — Despliegue del backend en el servidor propio (FT00052-PT1).
#
# Lo ejecuta el workflow `release-backend.yml` **en el servidor** por SSH, después
# de fijar el checkout en el tag publicado (`vX.Y.Z`). Pasos, en orden y
# **fail-fast**: cualquier fallo sale con código ≠ 0, deja el run de GitHub
# Actions en rojo y **no** se marca el release como bueno.
#
#   1. PostgreSQL (`docker compose up -d postgres` + espera a `healthy`).
#   2. Migraciones **Flyway** (tarea Gradle del host si hay JDK; si no, el
#      contenedor oficial `flyway/flyway` contra `127.0.0.1`).
#   3. `docker compose pull backend` + `docker compose up -d` (recrea el backend
#      con la imagen publicada en GHCR; `postgres` y `nginx` siguen como estaban).
#   4. Health-check: contenedor `healthy` + `GET /health` por HTTPS (200).
#
# Variables de entorno:
#   BACKEND_VERSION  (obligatoria) versión a desplegar, sin `v` (p. ej. `0.7.0`).
#   BACKEND_IMAGE    (obligatoria) imagen de GHCR publicada por el workflow.
#   FLYWAY_MODE      `auto` (default) | `gradle` | `container`.
#   HEALTH_CHECK_URL URL del health-check (default `https://$DOMAIN/health`).
#   DEPLOY_HEALTH_TIMEOUT_SECONDS   espera máxima del backend (default 180).
#   POSTGRES_HEALTH_TIMEOUT_SECONDS espera máxima de PostgreSQL (default 120).
#   HEALTH_CHECK_INTERVAL_SECONDS   intervalo entre comprobaciones (default 5).
#
# **Sin secretos por argumento**: `deploy/.env.production` (ignorado por git) es la
# fuente de verdad del servidor; el release solo fija la versión y la imagen.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="$repo_root/deploy/docker-compose.prod.yml"
env_file="$repo_root/deploy/.env.production"
postgres_timeout="${POSTGRES_HEALTH_TIMEOUT_SECONDS:-120}"
backend_timeout="${DEPLOY_HEALTH_TIMEOUT_SECONDS:-180}"

log() {
    echo "==> $*"
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

# `docker compose` del stack de producción, con el entorno del servidor.
compose() {
    docker compose -f "$compose_file" --env-file "$env_file" "$@"
}

require_release_env() {
    if [ -z "${BACKEND_VERSION:-}" ]; then
        fail "falta BACKEND_VERSION (versión a desplegar, sin la 'v'; p. ej. 0.7.0)."
    fi
    if [ -z "${BACKEND_IMAGE:-}" ]; then
        fail "falta BACKEND_IMAGE (imagen de GHCR; p. ej. ghcr.io/owner/datopublico-backend)."
    fi
    if [ ! -f "$env_file" ]; then
        fail "no existe $env_file: cópialo de deploy/.env.production.example y rellénalo."
    fi
    if ! command -v docker >/dev/null 2>&1; then
        fail "no se encontró 'docker' en el servidor."
    fi
}

# Carga el entorno de producción y deja que el release mande sobre él (el entorno
# del shell tiene prioridad sobre `--env-file` en Docker Compose).
load_environment() {
    local release_version="${BACKEND_VERSION#v}" release_image="$BACKEND_IMAGE"
    set -a
    # shellcheck disable=SC1090
    . "$env_file"
    set +a
    export BACKEND_VERSION="$release_version"
    export BACKEND_IMAGE="$release_image"
    log "Despliegue de ${BACKEND_IMAGE}:${BACKEND_VERSION}"
}

validate_environment() {
    if [ -z "${DOMAIN:-}" ]; then
        fail "falta DOMAIN en $env_file (lo necesitan Nginx y el health-check)."
    fi
    if [ -z "${POSTGRES_PASSWORD:-}" ]; then
        fail "falta POSTGRES_PASSWORD en $env_file."
    fi
}

# Id del contenedor del servicio (vacío si todavía no existe).
container_id() {
    compose ps -q "$1" 2>/dev/null | head -n 1 || true
}

# Espera a que un servicio esté `healthy` según el healthcheck de Compose.
wait_until_healthy() {
    local service="$1" timeout="$2" deadline status id
    deadline=$((SECONDS + timeout))
    status="sin contenedor"
    while [ "$SECONDS" -lt "$deadline" ]; do
        id="$(container_id "$service")"
        if [ -n "$id" ]; then
            status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo desconocido)"
        fi
        case "$status" in
            healthy)
                log "servicio '$service': healthy"
                return 0
                ;;
            unhealthy | exited | dead)
                log "servicio '$service': $status"
                return 1
                ;;
        esac
        sleep "${HEALTH_CHECK_INTERVAL_SECONDS:-5}"
    done
    log "servicio '$service': $status tras ${timeout}s de espera"
    return 1
}

start_database() {
    log "1/4 PostgreSQL (publicado solo en loopback)"
    compose up -d postgres
    if ! wait_until_healthy postgres "$postgres_timeout"; then
        compose logs --tail 50 postgres || true
        fail "PostgreSQL no alcanzó 'healthy' en ${postgres_timeout}s."
    fi
}

run_flyway_gradle() {
    log "Flyway con la tarea Gradle del host"
    (cd "$repo_root" && ./gradlew :backend:flywayMigrate --no-daemon)
}

# Alternativa sin JDK en el servidor: el contenedor oficial de Flyway, en la red
# del host, contra el PostgreSQL de loopback (mismo comando que el README de FT00041).
run_flyway_container() {
    local image="${FLYWAY_IMAGE:-flyway/flyway:11-alpine}"
    log "Flyway con el contenedor $image (PostgreSQL en 127.0.0.1)"
    docker run --rm --network host \
        -v "$repo_root/backend/src/main/resources/db/migration:/flyway/sql:ro" \
        -e "FLYWAY_URL=jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-datopublico}" \
        -e "FLYWAY_USER=${POSTGRES_USER:-datopublico}" \
        -e "FLYWAY_PASSWORD=${POSTGRES_PASSWORD}" \
        -e "FLYWAY_CLEAN_DISABLED=true" \
        "$image" migrate
}

migrate_database() {
    log "2/4 Migraciones de base de datos (Flyway)"
    case "${FLYWAY_MODE:-auto}" in
        gradle) run_flyway_gradle ;;
        container) run_flyway_container ;;
        auto)
            if command -v java >/dev/null 2>&1 || [ -n "${JAVA_HOME:-}" ]; then
                run_flyway_gradle
            else
                log "sin JDK en el servidor: se usa el contenedor de Flyway"
                run_flyway_container
            fi
            ;;
        *) fail "FLYWAY_MODE='${FLYWAY_MODE}' no es válido (auto|gradle|container)." ;;
    esac
}

deploy_image() {
    log "3/4 Imagen ${BACKEND_IMAGE}:${BACKEND_VERSION} y stack"
    compose pull backend
    compose up -d
    compose ps
}

check_health_endpoint() {
    local url="${HEALTH_CHECK_URL:-https://${DOMAIN}/health}"
    if command -v curl >/dev/null 2>&1; then
        log "GET $url"
        curl -fsS --max-time 15 -o /dev/null "$url" || fail "el health-check HTTP falló ($url)."
        log "health-check HTTP OK ($url)"
    elif command -v wget >/dev/null 2>&1; then
        log "GET $url"
        wget -q -T 15 -O /dev/null "$url" || fail "el health-check HTTP falló ($url)."
        log "health-check HTTP OK ($url)"
    else
        log "aviso: sin 'curl' ni 'wget'; se omite el health-check HTTP (el contenedor está healthy)"
    fi
}

check_health() {
    log "4/4 Health-check"
    if ! wait_until_healthy backend "$backend_timeout"; then
        compose logs --tail 100 backend || true
        fail "el backend no alcanzó 'healthy' en ${backend_timeout}s (revisa los logs de arriba)."
    fi
    check_health_endpoint
}

main() {
    require_release_env
    load_environment
    validate_environment
    start_database
    migrate_database
    deploy_image
    check_health
    log "OK: backend ${BACKEND_VERSION} desplegado (${BACKEND_IMAGE})"
}

main "$@"
