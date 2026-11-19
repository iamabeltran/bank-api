-- Dinero siempre en DECIMAL, nunca en FLOAT/DOUBLE.
CREATE TABLE accounts (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    owner_name  VARCHAR(120)   NOT NULL,
    balance     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency    CHAR(3)        NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,   -- para bloqueo optimista (@Version)
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Última línea de defensa: la BD misma impide saldos negativos.
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
) ENGINE = InnoDB;

CREATE TABLE transactions (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    type             VARCHAR(20)    NOT NULL,          -- DEPOSIT | TRANSFER
    from_account     BIGINT         NULL,              -- NULL en depósitos
    to_account       BIGINT         NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    currency         CHAR(3)        NOT NULL,
    idempotency_key  VARCHAR(64)    NULL,              -- NULL permitido varias veces en UNIQUE
    status           VARCHAR(20)    NOT NULL,          -- COMPLETED | FAILED
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- La garantía REAL de idempotencia vive en la base de datos.
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_transactions_from FOREIGN KEY (from_account) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_to   FOREIGN KEY (to_account)   REFERENCES accounts (id)
) ENGINE = InnoDB;

-- OJO: aquí NO creamos el índice (from_account, created_at) a propósito.
-- Primero mediremos con EXPLAIN sin índice y lo agregaremos en V2 (fase 6).
