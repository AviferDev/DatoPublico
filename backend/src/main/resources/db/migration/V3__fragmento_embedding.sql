-- Migración de indexado vectorial (FT00016).
--
-- Añade la columna de embedding a `fragmento` y su índice HNSW (distancia
-- coseno). V1/V2 no se editan (regla de migraciones Flyway).
--
-- La columna es **nullable**: los fragmentos se vectorizan después de crearse
-- (el indexado real es de FT00017/el backfill) y la consulta por similitud filtra
-- `embedding IS NOT NULL`.
--
-- `vector_cosine_ops` fija la distancia coseno (`<=>`), que es la que documenta
-- la skill RAG; los vectores de FT00015 ya vienen normalizados (L2). Cambiar el
-- modelo de embeddings (p. ej. `-base`, 768 dims) exigiría una migración nueva y
-- reindexar: `V3` no se edita.
--
-- `ALTER TABLE ADD COLUMN` nullable sin default es **solo metadato** en
-- PostgreSQL ≥11; la operación pesada es la construcción del índice HNSW (en
-- tablas grandes, valorar `maintenance_work_mem` o crear el índice tras la carga).

ALTER TABLE fragmento ADD COLUMN embedding vector(384);

CREATE INDEX fragmento_embedding_hnsw_idx
    ON fragmento USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
