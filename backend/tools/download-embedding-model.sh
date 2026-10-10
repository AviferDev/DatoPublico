#!/usr/bin/env bash
#
# Descarga manual (con red, FUERA del gate) de los artefactos de embeddings
# locales de FT00015: el export ONNX int8 de `intfloat/multilingual-e5-small`
# (`onnx/model_quantized.onnx`) y su `tokenizer.json` (XLM-R).
#
# El modelo (~118 MB) y el tokenizador (~17 MB) NO se versionan: se guardan en
# `backend/models/`, ignorado por git. Este script nunca se ejecuta en
# `build`/`test`/`init.sh`/CI; es un paso de aprovisionamiento explícito.
#
# Uso (desde cualquier directorio):
#   backend/tools/download-embedding-model.sh
#
# Variables opcionales:
#   EMBEDDING_MODEL_DIR       directorio destino (por defecto backend/models/multilingual-e5-small)
#   EMBEDDING_MODEL_BASE_URL  base del repo en Hugging Face (por defecto el espejo Xenova)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODEL_DIR="${EMBEDDING_MODEL_DIR:-$REPO_ROOT/backend/models/multilingual-e5-small}"

BASE_URL="${EMBEDDING_MODEL_BASE_URL:-https://huggingface.co/Xenova/multilingual-e5-small/resolve/main}"
MODEL_URL="$BASE_URL/onnx/model_quantized.onnx"
TOKENIZER_URL="$BASE_URL/tokenizer.json"

MODEL_FILE="$MODEL_DIR/model.onnx"
TOKENIZER_FILE="$MODEL_DIR/tokenizer.json"

download() {
    local url="$1"
    local dest="$2"
    if [ -f "$dest" ]; then
        echo "Ya existe: $dest"
    else
        echo "Descargando $url"
        curl -fL --retry 3 --retry-delay 2 -o "$dest.part" "$url"
        mv "$dest.part" "$dest"
    fi
}

mkdir -p "$MODEL_DIR"
download "$MODEL_URL" "$MODEL_FILE"
download "$TOKENIZER_URL" "$TOKENIZER_FILE"

echo
echo "Artefactos listos en $MODEL_DIR:"
ls -lh "$MODEL_FILE" "$TOKENIZER_FILE"
echo
echo "Variables de entorno para el proveedor (rutas absolutas):"
echo "  export EMBEDDING_MODEL_PATH=$MODEL_FILE"
echo "  export EMBEDDING_TOKENIZER_PATH=$TOKENIZER_FILE"
echo
echo "Test opt-in contra el modelo real (fuera del gate):"
echo "  EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*E5EmbeddingLiveTest'"
