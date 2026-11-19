-- Mide el historial paginado ANTES y DESPUÉS de los índices compuestos.
-- Trabaja sobre una tabla de pruebas (perf_transactions): NO toca tus datos reales.
--
-- Uso (PowerShell, con el contenedor de docker-compose en marcha):
--   Get-Content docs\performance\measure.sql | docker exec -i bank-mysql mysql -ubank -pbank bank
-- Uso (bash):
--   docker exec -i bank-mysql mysql -ubank -pbank bank < docs/performance/measure.sql
--
-- La cuenta 1 participa en TODAS las filas (mitad como origen, mitad como destino): es el peor caso.

DROP TABLE IF EXISTS perf_transactions;

CREATE TABLE perf_transactions (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    type         VARCHAR(20)    NOT NULL,
    from_account BIGINT         NULL,
    to_account   BIGINT         NOT NULL,
    amount       DECIMAL(19, 4) NOT NULL,
    currency     CHAR(3)        NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_perf_from (from_account),   -- equivale al índice que InnoDB crea por la clave foránea
    KEY idx_perf_to (to_account)
) ENGINE = InnoDB;

SET SESSION cte_max_recursion_depth = 600000;

INSERT INTO perf_transactions (type, from_account, to_account, amount, currency, status, created_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 500000
)
SELECT 'TRANSFER',
       IF(n % 2 = 0, 1, 2 + (n % 98)),
       IF(n % 2 = 0, 2 + (n % 98), 1),
       1 + (n % 1000),
       'MXN',
       'COMPLETED',
       TIMESTAMP('2026-01-01 00:00:00') + INTERVAL n SECOND
FROM seq;

ANALYZE TABLE perf_transactions;
SELECT COUNT(*) AS total_rows FROM perf_transactions;

-- =====================================================================
-- ANTES: solo índices de una columna
-- =====================================================================
SELECT '--- ANTES: página 0 (size 20) ---' AS etapa;
EXPLAIN ANALYZE
SELECT h.* FROM (
  (SELECT * FROM perf_transactions WHERE from_account = 1 ORDER BY created_at DESC, id DESC LIMIT 20)
  UNION ALL
  (SELECT * FROM perf_transactions WHERE to_account = 1 ORDER BY created_at DESC, id DESC LIMIT 20)
) AS h
ORDER BY h.created_at DESC, h.id DESC
LIMIT 20 OFFSET 0;

SELECT '--- ANTES: página 5 (size 20, offset 100) ---' AS etapa;
EXPLAIN ANALYZE
SELECT h.* FROM (
  (SELECT * FROM perf_transactions WHERE from_account = 1 ORDER BY created_at DESC, id DESC LIMIT 120)
  UNION ALL
  (SELECT * FROM perf_transactions WHERE to_account = 1 ORDER BY created_at DESC, id DESC LIMIT 120)
) AS h
ORDER BY h.created_at DESC, h.id DESC
LIMIT 20 OFFSET 100;

-- =====================================================================
-- DESPUÉS: índices compuestos (los mismos que crea la migración V2)
-- =====================================================================
ALTER TABLE perf_transactions
    ADD INDEX idx_perf_from_created (from_account, created_at),
    ADD INDEX idx_perf_to_created (to_account, created_at);
ANALYZE TABLE perf_transactions;

SELECT '--- DESPUES: página 0 (size 20) ---' AS etapa;
EXPLAIN ANALYZE
SELECT h.* FROM (
  (SELECT * FROM perf_transactions WHERE from_account = 1 ORDER BY created_at DESC, id DESC LIMIT 20)
  UNION ALL
  (SELECT * FROM perf_transactions WHERE to_account = 1 ORDER BY created_at DESC, id DESC LIMIT 20)
) AS h
ORDER BY h.created_at DESC, h.id DESC
LIMIT 20 OFFSET 0;

SELECT '--- DESPUES: página 5 (size 20, offset 100) ---' AS etapa;
EXPLAIN ANALYZE
SELECT h.* FROM (
  (SELECT * FROM perf_transactions WHERE from_account = 1 ORDER BY created_at DESC, id DESC LIMIT 120)
  UNION ALL
  (SELECT * FROM perf_transactions WHERE to_account = 1 ORDER BY created_at DESC, id DESC LIMIT 120)
) AS h
ORDER BY h.created_at DESC, h.id DESC
LIMIT 20 OFFSET 100;

-- Limpieza (opcional): DROP TABLE perf_transactions;
