package com.portfolio.bankapi.transaction;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Una página del historial de una cuenta (movimientos como origen O como destino), del más reciente
     * al más antiguo. Se resuelve con dos recorridos de índice acotados (uno por dirección) y una mezcla
     * final, en lugar de un "WHERE from = ? OR to = ?" que obligaría a ordenar todo el historial.
     * Requiere los índices (from_account, created_at) y (to_account, created_at) de la migración V2.
     *
     * @param window offset + size: cuántas filas del extremo reciente necesita cada lado como máximo
     */
    @Query(value = """
            SELECT h.* FROM (
              (SELECT * FROM transactions WHERE from_account = :accountId
               ORDER BY created_at DESC, id DESC LIMIT :window)
              UNION ALL
              (SELECT * FROM transactions WHERE to_account = :accountId
               ORDER BY created_at DESC, id DESC LIMIT :window)
            ) AS h
            ORDER BY h.created_at DESC, h.id DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Transaction> findHistoryPage(@Param("accountId") Long accountId,
                                      @Param("window") long window,
                                      @Param("size") int size,
                                      @Param("offset") long offset);

    /** Total de movimientos de la cuenta (un movimiento nunca tiene la misma cuenta como origen y destino). */
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM transactions WHERE from_account = :accountId)
                 + (SELECT COUNT(*) FROM transactions WHERE to_account = :accountId)
            """, nativeQuery = true)
    long countHistory(@Param("accountId") Long accountId);
}
