package com.portfolio.bankapi.transfer;

import com.portfolio.bankapi.transaction.Transaction;

/** Resultado de una transferencia; {@code replayed} es true si se devolvió el movimiento de una petición anterior. */
public record TransferResult(Transaction transaction, boolean replayed) {
}
