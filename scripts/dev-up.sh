#!/usr/bin/env bash
#
# dev-up.sh — Arranca el entorno de desarrollo local de DatoPublico.
#
# Hace cuatro cosas:
#   1. Baja cualquier otro stack Compose de DatoPublico lanzado desde este
#      repositorio o desde alguno de sus worktrees (mismo `.git`), para que no se
#      pisen los puertos publicados (`5432`) ni el nombre fijo del contenedor
#      (`datopublico-postgres`). Incluye los worktrees ya eliminados de git cuyos
#      contenedores siguen vivos. No toca contenedores de otros proyectos Compose.
#   2. Levanta PostgreSQL/pgvector con `docker compose up -d --wait` (espera al
#      healthcheck).
#   3. Aplica las migraciones Flyway (`:backend:flywayMigrate`).
#   4. Reinicia el backend (`:backend:run`) en segundo plano y espera a `/health`.
#      Si detecta el modelo E5 en `backend/models/`, habilita búsqueda y chat.
#      El cliente (app Android / web) lo arrancas tú a mano.
#
# El `.env` de la raíz es la fuente de verdad (lo leen Docker Compose y Flyway, y
# el script lo exporta para el backend). Para que el fichero gane sobre el entorno
# del shell, se desexportan aquí (solo en el proceso del script) las claves
# definidas en `.env` que el shell traiga.
#
# Uso:
#   ./scripts/dev-up.sh                 # Compose + migraciones + backend
#   ./scripts/dev-up.sh --no-backend    # sin arrancar el backend
#   ./scripts/dev-up.sh --no-migrate    # sin migraciones
#
# El log del backend queda en `build/dev-up/backend.log` (ignorado por git).
#
# Requisitos: `docker` (Compose v2), `git`, un JDK 21 (o el JBR de Android Studio)
# y un `.env` en la raíz (se crea desde `.env.example` si falta). No se versiona
# ningún secreto.
set -euo pipefail

usage() {
    sed -n '2,33p' "$0" | sed 's/^# \{0,1\}//'
}

# Raíz del worktree actual (donde viven `docker-compose.yml` y `.env`).
cd "$(git rev-parse --show-toplevel)"

migrate=1
with_backend=1
for arg in "$@"; do
    case "$arg" in
        --no-migrate) migrate=0 ;;
        --no-backend) with_backend=0 ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            echo "Argumento no reconocido: $arg" >&2
            exit 2
            ;;
    esac
done

# Proyecto Compose por defecto: nombre del directorio, en minúsculas (Compose lo
# normaliza; comparar en minúsculas evita bajar tu propio stack por error).
current_project="$(printf '%s' "${COMPOSE_PROJECT_NAME:-$(basename "$PWD")}" | tr '[:upper:]' '[:lower:]')"

# El `.env` del worktree gana sobre el entorno del shell.
scrub_shadowing_env() {
    [ -f .env ] || return 0
    local key shadowed=""
    while IFS= read -r key; do
        if [ "$key" != "COMPOSE_PROJECT_NAME" ] && [ -n "${!key+x}" ]; then
            shadowed="${shadowed}${key} "
            unset "$key"
        fi
    done < <(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' .env)
    if [ -n "$shadowed" ]; then
        echo "==> Ignorando variables del shell que pisaban el .env: ${shadowed% }"
    fi
}

# Crea el `.env` desde el ejemplo si falta (con aviso para revisar la contraseña).
ensure_dotenv() {
    [ -f .env ] && return 0
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "==> Creado .env desde .env.example. Revisa POSTGRES_PASSWORD antes de continuar."
        exit 0
    fi
    echo "ERROR: falta .env (y no hay .env.example para copiar)." >&2
    exit 1
}

# Resuelve un JAVA_HOME para Gradle (JDK 21): entorno, JBR de Android Studio, PATH.
resolve_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        return 0
    fi
    local candidate
    for candidate in "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        if [ -x "$candidate/bin/java" ]; then
            export JAVA_HOME="$candidate"
            return 0
        fi
    done
    command -v java >/dev/null 2>&1
}

# Habilita búsqueda/chat si el modelo E5 está descargado y no se fijó a mano.
enable_embeddings_if_available() {
    [ -n "${EMBEDDING_MODEL_PATH:-}" ] && return 0
    local dir="backend/models/multilingual-e5-small"
    [ -f "$dir/model.onnx" ] || return 0
    export EMBEDDING_MODEL_PATH="$PWD/$dir/model.onnx"
    export EMBEDDING_TOKENIZER_PATH="$PWD/$dir/tokenizer.json"
    echo "==> Modelo E5 detectado: búsqueda y chat habilitados."
}

# ¿Sigue vivo el proceso del backend que lanzamos (PID del job en segundo plano)?
backend_process_alive() {
    kill -0 "$1" 2>/dev/null
}

# ¿Responde el backend de *este* proyecto? No basta un `200` cualquiera: el cuerpo
# de `/health` debe ser `{"status":"ok"}` y `/openapi.json` debe responder (un
# túnel SSH u otro servidor ocupando el 8080 no lo cumple).
backend_is_ours() {
    local body
    body="$(curl -fsS -m 2 "http://localhost:8080/health" 2>/dev/null | tr -d '[:space:]')"
    [ "$body" = '{"status":"ok"}' ] && curl -fsS -m 2 -o /dev/null "http://localhost:8080/openapi.json" 2>/dev/null
}

# Espera (acotado) a que *nuestro* backend esté listo: exige que el job lanzado
# siga vivo y que responda como el backend de este proyecto. Si el proceso muere
# o se agota el tiempo, es un **fallo**: mensaje a stderr y salida distinta de 0.
wait_for_health() {
    local pid="$1" i ready=1
    for i in $(seq 1 180); do
        if backend_process_alive "$pid" && backend_is_ours; then
            echo "==> Backend listo en http://localhost:8080 (tras ${i}s)."
            ready=0
        fi
        if [ "$ready" -eq 0 ] || ! backend_process_alive "$pid"; then
            break
        fi
        sleep 1
    done
    if [ "$ready" -ne 0 ]; then
        echo "ERROR: el backend no llegó a estar listo en 180s (o su proceso murió antes);" >&2
        echo "       revisa build/dev-up/backend.log." >&2
    fi
    return "$ready"
}

# Para el backend anterior, si lo hubiera (se relanza siempre para garantizar la
# configuración del `.env`, no una instancia ajena).
stop_backend() {
    pgrep -f "datopublico.backend.ApplicationKt" >/dev/null 2>&1 || return 0
    echo "==> Parando backend anterior"
    pkill -f "datopublico.backend.ApplicationKt" 2>/dev/null || true
    local i
    for i in $(seq 1 20); do
        curl -fsS -m 1 "http://localhost:8080/health" >/dev/null 2>&1 || break
        sleep 1
    done
    pkill -9 -f "datopublico.backend.ApplicationKt" 2>/dev/null || true
}

# Arranca el backend en segundo plano (reiniciándolo si ya había uno) y espera a
# que responda; si no llega a estar listo, propaga el fallo (con `set -e` el script
# termina sin imprimir el mensaje de éxito).
start_backend() {
    stop_backend
    local log_dir="build/dev-up" log
    mkdir -p "$log_dir"
    log="$log_dir/backend.log"
    echo "==> Arrancando backend (:backend:run); log: $log"
    nohup ./gradlew --no-daemon --quiet :backend:run >"$log" 2>&1 &
    backend_pid=$!
    disown 2>/dev/null || true
    wait_for_health "$backend_pid"
}

ensure_dotenv
scrub_shadowing_env

# Exporta el `.env` al entorno del proceso: el backend lee `System.getenv()`.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

# Worktrees registrados en git (árbol principal incluido), uno por línea.
worktree_dirs="$(git worktree list --porcelain | sed -n 's/^worktree //p')"

# Directorio de worktrees del repositorio: se admiten las convenciones
# `<repo>-wt` (la usada por este repo, p. ej. `DatoPublico-wt`) y `<repo>.worktrees`.
# Puede contener worktrees ya eliminados de git cuyos contenedores siguen vivos.
repo_root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
base="$(basename "$repo_root")"
worktrees_root_a="$(dirname "$repo_root")/${base}-wt"
worktrees_root_b="$(dirname "$repo_root")/${base}.worktrees"

# Un directorio pertenece a este repositorio si es un worktree registrado o si
# cuelga del directorio de worktrees (aunque ya no esté en git).
belongs_to_repo() {
    local dir="$1" found=""
    if printf '%s\n' "$worktree_dirs" | grep -qxF "$dir"; then
        found="yes"
    fi
    case "$dir" in
        "$worktrees_root_a"/* | "$worktrees_root_b"/*) found="yes" ;;
    esac
    [ -n "$found" ]
}

# Proyectos Compose cuyos contenedores se lanzaron desde alguno de esos
# directorios (el `working_dir` de Compose es la raíz del worktree).
dato_projects="$(
    docker ps --all \
        --format '{{.Label "com.docker.compose.project"}}|{{.Label "com.docker.compose.project.working_dir"}}' \
    | while IFS='|' read -r project working_dir; do
        if [ -n "$project" ] && belongs_to_repo "$working_dir"; then
            printf '%s\n' "$project"
        fi
    done | sort -u
)"

# Baja los demás stacks de DatoPublico; el actual lo recrea el `up` del final.
for project in $dato_projects; do
    if [ "$project" != "$current_project" ]; then
        echo "==> Bajando stack de DatoPublico: $project"
        docker compose --project-name "$project" down --remove-orphans
    fi
done

echo "==> Levantando PostgreSQL (proyecto: $current_project)"
docker compose up --detach --wait --wait-timeout 60

if ! resolve_java; then
    echo "==> Aviso: no se encontró un JDK; se omiten migraciones y backend." >&2
    echo "    Define JAVA_HOME y ejecuta: ./gradlew :backend:flywayMigrate :backend:run" >&2
    exit 0
fi

if [ "$migrate" -eq 1 ]; then
    echo "==> Aplicando migraciones Flyway"
    ./gradlew --quiet :backend:flywayMigrate
fi

if [ "$with_backend" -eq 1 ]; then
    enable_embeddings_if_available
    start_backend
fi

cat <<'NEXT'

==> Listo. El backend está en marcha en http://localhost:8080
    - Android:  adb reverse tcp:8080 tcp:8080   # y luego :composeApp:assembleDebug + install
    - Web:      ./gradlew :composeApp:wasmJsBrowserDistribution
    - Backend:  para pararlo, `pkill -f datopublico.backend.ApplicationKt`
    Ver docs/desarrollo-local.md para el detalle.
NEXT
