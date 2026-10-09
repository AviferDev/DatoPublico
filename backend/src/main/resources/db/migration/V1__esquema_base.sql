-- Migración base de DatoPublico (FT00003).
--
-- Deja el esquema listo para la persistencia: habilita pgvector y una tabla
-- mínima de control del esquema. Las tablas de dominio (publicacion, fragmento,
-- resumen, cola_revision) llegan con FT00008 y la columna de embedding con índice
-- HNSW (dimensión 384) con FT00016.
--
-- `CREATE EXTENSION` requiere superusuario; en la imagen oficial de pgvector el
-- usuario de `POSTGRES_USER` lo es.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE esquema_info (
    clave          text PRIMARY KEY,
    valor          text NOT NULL,
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

INSERT INTO esquema_info (clave, valor)
VALUES ('esquema_base', 'FT00003');
