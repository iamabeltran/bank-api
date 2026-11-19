package com.portfolio.bankapi.transfer;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountRepository;
import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.IdempotencyConflictException;
import com.portfolio.bankapi.error.InvalidTransferException;
import com.portfolio.bankapi.transaction.Transaction;
import com.portfolio.bankapi.transaction.TransactionRepository;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public TransferService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    /** Transferencia sin clave de idempotencia. */
    @Transactional
    public Transaction transfer(Long fromId, Long toId, BigDecimal amount) {
        return execute(null, fromId, toId, amount).transaction();
    }

    /**
     * Transferencia idempotente: con la misma clave y la misma petición, el dinero se mueve UNA sola vez
     * y las repeticiones devuelven el movimiento original (replayed = true). Con la misma clave y una
     * petición distinta se lanza {@link IdempotencyConflictException}. Con clave nula se comporta como
     * la transferencia normal.
     */
    @Transactional
    public TransferResult transfer(String idempotencyKey, Long fromId, Long toId, BigDecimal amount) {
        return execute(idempotencyKey, fromId, toId, amount);
    }

    /**
     * Mueve dinero entre dos cuentas en UNA sola transacción de base de datos.
     * Si cualquier paso falla, el rollback deshace todo: nunca se pierde ni se crea dinero.
     */
    private TransferResult execute(String idempotencyKey, Long fromId, Long toId, BigDecimal amount) {
        if (fromId.equals(toId)) {
            throw new InvalidTransferException("Source and destination accounts must be different");
        }

        // Anti-deadlock: bloqueamos SIEMPRE primero la cuenta con id menor, sin importar
        // cuál sea el origen. Así, A->B y B->A concurrentes se ponen "en fila" en vez de
        // esperarse mutuamente para siempre.
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);
        Account first = lock(firstId);
        Account second = lock(secondId);

        Account from = fromId.equals(firstId) ? first : second;
        Account to = toId.equals(firstId) ? first : second;

        // La clave se consulta DESPUÉS de bloquear las cuentas: dos peticiones duplicadas sobre las mismas
        // cuentas se ponen en fila, y la segunda ya ve (y reutiliza) el movimiento confirmado por la primera,
        // incluso si el saldo restante ya no alcanzaría para repetir la transferencia.
        if (idempotencyKey != null) {
            Optional<Transaction> previous = transactions.findByIdempotencyKey(idempotencyKey);
            if (previous.isPresent()) {
                return replay(previous.get(), idempotencyKey, fromId, toId, amount);
            }
        }

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new InvalidTransferException("Accounts must have the same currency");
        }

        from.withdraw(amount); // lanza InsufficientFundsException si no alcanza
        to.deposit(amount);

        return new TransferResult(save(Transaction.transfer(from, to, amount, idempotencyKey), idempotencyKey), false);
    }

    private TransferResult replay(Transaction previous, String key, Long fromId, Long toId, BigDecimal amount) {
        boolean samePayload = fromId.equals(previous.getFromAccountId())
                && toId.equals(previous.getToAccountId())
                && amount.compareTo(previous.getAmount()) == 0;
        if (!samePayload) {
            throw new IdempotencyConflictException(key);
        }
        return new TransferResult(previous, true);
    }

    /**
     * Última red de seguridad: si otra petición con la misma clave pero sobre OTRAS cuentas ganó la carrera,
     * la restricción UNIQUE de la base de datos rechaza este insert y se reporta como conflicto.
     */
    private Transaction save(Transaction transaction, String idempotencyKey) {
        try {
            return transactions.save(transaction);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            throw e;
        }
    }

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
