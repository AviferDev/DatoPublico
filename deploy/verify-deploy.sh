#!/usr/bin/env bash
#
# Verificación offline del despliegue (FT00041). **OPT-IN**: NO la ejecuta el gate
# estándar ni la CI (el gate no arranca servidores ni Docker). Comprueba:
#   1) estáticamente que no hay secretos versionados y que `deploy/.env.production`
#      está ignorado por git (no necesita Docker);
#   2) si hay Docker, que `docker compose config` resuelve con la plantilla;
#   3) si el daemon de Docker responde, que la configuración de Nginx es válida
#      (`nginx -t` con certificados de prueba).
# Si falta Docker, omite (2) y (3) con un mensaje claro y termina en verde.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

compose_file="deploy/docker-compose.prod.yml"
env_template="deploy/.env.production.example"
domain="datopublico.example"

echo "[verify-deploy] 1/3 comprobación estática de secretos"

if ! git check-ignore -q deploy/.env.production; then
    echo "  ERROR: deploy/.env.production NO está ignorado por git" >&2
    exit 1
fi
if git ls-files --error-unmatch deploy/.env.production >/dev/null 2>&1; then
    echo "  ERROR: deploy/.env.production está versionado" >&2
    exit 1
fi
echo "  OK: deploy/.env.production está ignorado y no versionado"

# La plantilla solo lleva *placeholders* en los campos sensibles.
for key in POSTGRES_PASSWORD OPENCODE_API_KEY; do
    value="$(grep -E "^${key}=" "$env_template" | head -n 1 | cut -d= -f2- || true)"
    case "$value" in
        *REPLACE_*|*example*|*changeme*) echo "  OK: $key es un placeholder" ;;
        *)
            echo "  ERROR: $key parece un secreto real; usa un placeholder" >&2
            exit 1
            ;;
    esac
done

if ! command -v docker >/dev/null 2>&1; then
    echo "[verify-deploy] Docker no está instalado: se omiten Compose y Nginx (opt-in)."
    echo "[verify-deploy] OK (solo comprobaciones estáticas)"
    exit 0
fi

echo "[verify-deploy] 2/3 docker compose config"
docker compose -f "$compose_file" --env-file "$env_template" config -q
echo "  OK: la configuración resuelve (PostgreSQL en loopback, backend sin puerto)"

if ! docker info >/dev/null 2>&1; then
    echo "[verify-deploy] El daemon de Docker no responde: se omite el lint de Nginx (opt-in)."
    echo "[verify-deploy] OK (estático + Compose)"
    exit 0
fi

echo "[verify-deploy] 3/3 lint de la configuración de Nginx"
if ! command -v openssl >/dev/null 2>&1; then
    echo "  OMITIDO: falta openssl para generar certificados de prueba."
else
    certs="$(mktemp -d)"
    trap 'rm -rf "$certs"' EXIT
    mkdir -p "$certs/live/$domain"
    openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
        -keyout "$certs/live/$domain/privkey.pem" \
        -out "$certs/live/$domain/fullchain.pem" \
        -subj "/CN=$domain" >/dev/null 2>&1
    docker run --rm -e "DOMAIN=$domain" \
        -v "$repo_root/deploy/nginx/templates:/etc/nginx/templates:ro" \
        -v "$certs:/etc/letsencrypt:ro" \
        nginx:1.27-alpine nginx -t
    echo "  OK: la configuración de Nginx es válida"
fi

echo "[verify-deploy] OK"
