-- Historial paginado por cuenta (fase 6): recorrido por índice en orden cronológico inverso.
-- La medición con EXPLAIN ANALYZE antes/después está en docs/PERFORMANCE.md.
CREATE INDEX idx_transactions_from_created ON transactions (from_account, created_at);
CREATE INDEX idx_transactions_to_created ON transactions (to_account, created_at);
