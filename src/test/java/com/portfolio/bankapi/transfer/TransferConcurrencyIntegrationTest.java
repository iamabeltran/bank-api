package com.portfolio.bankapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountService;
import com.portfolio.bankapi.error.InsufficientFundsException;
import com.portfolio.bankapi.support.IntegrationTest;
import com.portfolio.bankapi.transaction.Transaction;
import com.portfolio.bankapi.transaction.TransactionRepository;
import com.portfolio.bankapi.transaction.TransactionType;

/**
 * Pruebas de concurrencia REALES: muchos hilos llamando a TransferService al mismo tiempo
 * contra MySQL. Las aserciones son exactas (no "aproximadas"): si faltara el bloqueo de filas,
 * habría actualizaciones perdidas y los números no cuadrarían; si faltara el orden de bloqueo,
 * aparecerían deadlocks (excepciones inesperadas o el test se colgaría hasta el @Timeout).
 */
@IntegrationTest
class TransferConcurrencyIntegrationTest {

    private static final int THREADS = 16;

    @Autowired
    AccountService accountService;

    @Autowired
    TransferService transferService;

    @Autowired
    TransactionRepository transactions;

    // ------------------------------------------------------------------ pruebas

    @Test
    @Timeout(120)
    void crossedTransfersDoNotDeadlockAndBalancesAreExact() throws Exception {
        Account ana = openAccount("Ana", "10000");
        Account luis = openAccount("Luis", "10000");

        // 100 transferencias CRUZADAS: 60 de Ana a Luis y 40 de Luis a Ana, de 10 cada una.
        List<TransferCommand> commands = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            boolean anaToLuis = i % 5 < 3;
            commands.add(anaToLuis
                    ? new TransferCommand(ana.getId(), luis.getId(), BigDecimal.TEN)
                    : new TransferCommand(luis.getId(), ana.getId(), BigDecimal.TEN));
        }

        Result result = runConcurrently(commands);

        assertThat(result.unexpected).as("sin deadlocks ni errores inesperados").isEmpty();
        assertThat(result.succeeded.get()).isEqualTo(100);
        // Ana: 10000 - 60*10 + 40*10 = 9800 | Luis: 10000 + 60*10 - 40*10 = 10200
        assertThat(balanceOf(ana)).isEqualByComparingTo("9800");
        assertThat(balanceOf(luis)).isEqualByComparingTo("10200");
        assertThat(transfersAmong(Set.of(ana.getId(), luis.getId()))).hasSize(100);
    }

    @Test
    @Timeout(120)
    void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        Account origin = openAccount("Origen", "500");
        Account target = openAccount("Destino", "0");

        // 100 intentos de transferir 10 desde una cuenta que solo alcanza para 50.
        List<TransferCommand> commands = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            commands.add(new TransferCommand(origin.getId(), target.getId(), BigDecimal.TEN));
        }

        Result result = runConcurrently(commands);

        assertThat(result.unexpected).isEmpty();
        assertThat(result.succeeded.get()).as("exactamente 50 caben en 500").isEqualTo(50);
        assertThat(result.insufficientFunds.get()).isEqualTo(50);
        assertThat(balanceOf(origin)).isEqualByComparingTo("0");
        assertThat(balanceOf(target)).isEqualByComparingTo("500");
    }

    @Test
    @Timeout(180)
    void randomTransfersAmongFiveAccountsConserveTheTotal() throws Exception {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            accounts.add(openAccount("Cuenta" + i, "1000"));
        }

        // Semilla fija: la carga es aleatoria pero REPRODUCIBLE.
        Random random = new Random(42);
        List<TransferCommand> commands = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int from = random.nextInt(5);
            int to = random.nextInt(4);
            if (to >= from) {
                to++; // garantiza origen != destino
            }
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(200));
            commands.add(new TransferCommand(accounts.get(from).getId(), accounts.get(to).getId(), amount));
        }

        Result result = runConcurrently(commands);

        assertThat(result.unexpected).isEmpty();
        assertThat(result.succeeded.get() + result.insufficientFunds.get()).isEqualTo(300);

        BigDecimal total = BigDecimal.ZERO;
        for (Account account : accounts) {
            BigDecimal balance = balanceOf(account);
            assertThat(balance).as("saldo de " + account.getOwnerName()).isNotNegative();
            total = total.add(balance);
        }
        assertThat(total).as("el dinero ni se crea ni se destruye").isEqualByComparingTo("5000");

        Set<Long> ids = accounts.stream().map(Account::getId).collect(Collectors.toSet());
        assertThat(transfersAmong(ids)).hasSize(result.succeeded.get());
    }

    // ------------------------------------------------------------------ utilidades

    private record TransferCommand(Long from, Long to, BigDecimal amount) {
    }

    private static class Result {
        final AtomicInteger succeeded = new AtomicInteger();
        final AtomicInteger insufficientFunds = new AtomicInteger();
        final List<Throwable> unexpected = new CopyOnWriteArrayList<>();
    }

    /** Lanza todas las transferencias a la vez (compuerta de salida) y espera a que terminen. */
    private Result runConcurrently(List<TransferCommand> commands) throws Exception {
        Result result = new Result();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (TransferCommand command : commands) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        transferService.transfer(command.from(), command.to(), command.amount());
                        result.succeeded.incrementAndGet();
                    } catch (InsufficientFundsException e) {
                        result.insufficientFunds.incrementAndGet(); // resultado de negocio válido
                    } catch (Throwable t) {
                        result.unexpected.add(t); // deadlock, bloqueo optimista, timeout...
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
        return result;
    }

    private Account openAccount(String owner, String initialBalance) {
        Account account = accountService.create(owner, "MXN");
        BigDecimal amount = new BigDecimal(initialBalance);
        if (amount.signum() > 0) {
            accountService.deposit(account.getId(), amount);
        }
        return account;
    }

    private BigDecimal balanceOf(Account account) {
        return accountService.get(account.getId()).getBalance();
    }

    private List<Transaction> transfersAmong(Set<Long> accountIds) {
        return transactions.findAll().stream()
                .filter(tx -> tx.getType() == TransactionType.TRANSFER)
                .filter(tx -> accountIds.contains(tx.getFromAccountId()))
                .toList();
    }
}
