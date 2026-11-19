package com.portfolio.bankapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountService;
import com.portfolio.bankapi.error.IdempotencyConflictException;
import com.portfolio.bankapi.support.IntegrationTest;
import com.portfolio.bankapi.transaction.TransactionRepository;

/** Idempotencia contra MySQL real, incluida una prueba con duplicados simultáneos. */
@IntegrationTest
class TransferIdempotencyIntegrationTest {

    @Autowired
    AccountService accountService;

    @Autowired
    TransferService transferService;

    @Autowired
    TransactionRepository transactions;

    private Account open(String owner, String balance) {
        Account account = accountService.create(owner, "MXN");
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            accountService.deposit(account.getId(), amount);
        }
        return account;
    }

    private BigDecimal balanceOf(Account account) {
        return accountService.get(account.getId()).getBalance();
    }

    @Test
    void theSameKeyTwiceMovesTheMoneyOnlyOnce() {
        Account ana = open("Ana", "500");
        Account luis = open("Luis", "0");
        String key = UUID.randomUUID().toString();

        TransferResult first = transferService.transfer(key, ana.getId(), luis.getId(), new BigDecimal("200"));
        TransferResult second = transferService.transfer(key, ana.getId(), luis.getId(), new BigDecimal("200.00"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transaction().getId()).isEqualTo(first.transaction().getId());
        assertThat(balanceOf(ana)).isEqualByComparingTo("300");
        assertThat(balanceOf(luis)).isEqualByComparingTo("200");
        assertThat(transactions.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void theSameKeyWithADifferentAmountIsRejectedAndChangesNothing() {
        Account ana = open("Ana", "500");
        Account luis = open("Luis", "0");
        String key = UUID.randomUUID().toString();
        transferService.transfer(key, ana.getId(), luis.getId(), new BigDecimal("200"));

        assertThatThrownBy(() -> transferService.transfer(key, ana.getId(), luis.getId(), new BigDecimal("50")))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("300");
        assertThat(balanceOf(luis)).isEqualByComparingTo("200");
    }

    @Test
    @Timeout(120)
    void twentyParallelDuplicatesExecuteExactlyOnceEvenWhenFundsOnlyCoverOne() throws Exception {
        // Saldo suficiente para UNA sola transferencia: si los duplicados se reevaluaran, fallarían
        // por saldo insuficiente en lugar de devolver el resultado original.
        Account ana = open("Ana", "100");
        Account luis = open("Luis", "0");
        String key = UUID.randomUUID().toString();

        List<TransferResult> results = new CopyOnWriteArrayList<>();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        results.add(transferService.transfer(key, ana.getId(), luis.getId(), new BigDecimal("100")));
                    } catch (Throwable t) {
                        unexpected.add(t);
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(90, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected).as("ningún duplicado debe fallar").isEmpty();
        assertThat(results).hasSize(20);
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().filter(TransferResult::replayed).count()).isEqualTo(19);
        Set<Long> distinctIds = results.stream().map(r -> r.transaction().getId()).collect(Collectors.toSet());
        assertThat(distinctIds).as("todas devuelven el mismo movimiento").hasSize(1);
        assertThat(balanceOf(ana)).isEqualByComparingTo("0");
        assertThat(balanceOf(luis)).isEqualByComparingTo("100");
    }
}
