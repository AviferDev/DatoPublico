-- Migración de la cola de revisión idempotente (FT00023).
--
-- Crea un **índice único parcial** que permite como mucho **una** entrada
-- `pendiente` por resumen, garantizando la idempotencia del encolado (el servicio
-- de disposición hace *upsert* por `resumen_id`), y conserva el historial de
-- revisiones ya cerradas (`revisado`,`descartado`…). No toca `resumen` ni añade
-- columnas.
--
-- V1/V2/V3/V4 no se editan (regla de migraciones Flyway). La migración queda
-- **fuera** de `build`/`check` (se aplica con `./gradlew :backend:flywayMigrate`).
--
-- Nota: crear el índice falla si ya existieran duplicados pendientes; en la
-- práctica `cola_revision` está vacía (hasta FT00023 nadie escribía en ella).

CREATE UNIQUE INDEX cola_revision_pendiente_unique
    ON cola_revision (resumen_id)
    WHERE estado = 'pendiente';
