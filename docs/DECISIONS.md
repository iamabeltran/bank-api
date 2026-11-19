# Design decisions

Each decision is recorded when it is made, together with its reasoning.

| # | Decision | Why |
|---|---|---|
| 1 | Maven as the build tool | Standard in banking and large enterprises; most Spring teams use it |
| 2 | `DECIMAL(19,4)` / `BigDecimal` for money | `double` loses precision (0.1 + 0.2 != 0.3) |
| 3 | Flyway owns the schema; Hibernate only validates | The schema is versioned and reproducible |
| 4 | Integration tests on real MySQL (Testcontainers), not H2 | H2 behaves differently for locks, types and constraints |
| 5 | Index on `transactions(from_account, created_at)` initially deferred | Deferred until the query and `EXPLAIN` were ready; later added in migration V2 to measure before and after |
| 6 | Business rules (`deposit`, `withdraw`) live in the `Account` entity | The entity never ends up in an invalid state; services only orchestrate |
| 7 | Errors as `ProblemDetail` (RFC 7807) in a `@RestControllerAdvice` | Standard, consistent format; controllers contain no `try/catch` |
| 8 | Deposit uses `SELECT ... FOR UPDATE` (pessimistic lock) | Concurrent deposits to the same account are serialized; same mechanism as transfers |
| 9 | Deposit and ledger entry in the same `@Transactional` | Atomicity: balance and ledger can never drift apart |
| 10 | `@JdbcTypeCode(SqlTypes.CHAR)` on `currency` instead of editing migration V1 | An applied migration is never edited (Flyway validates its checksum) |
| 11 | Custom `@IntegrationTest` annotation for DB-backed tests | One annotation starts the app against MySQL in Docker and skips the tests if Docker is unavailable |
| 12 | `TransferService` separate from `AccountService` | One responsibility per service; the transfer is the critical flow |
| 13 | Transfers lock both accounts always in ascending id order | If A→B and B→A arrive together, both request the lower id first: no deadlock possible |
| 14 | Insufficient funds, same account and currency mismatch return `422` | The request is syntactically valid but violates a business rule |
| 15 | Recording `FAILED` transfers deferred | Would need an independent transaction (`REQUIRES_NEW`) because the main one rolls back |
| 16 | Testcontainers pinned to 1.21.4 (`testcontainers.version`) | The version managed by Spring Boot 3.3.5 does not work with recent Docker Engines (API 1.44+ required) |
| 17 | A single shared MySQL container, declared as a Spring bean (`@ServiceConnection`) | Spring caches the context: one container for the whole suite, shut down after the connection pool closes |
| 18 | Concurrency tests with exact assertions and a fixed random seed | Without locking, or without lock ordering, the numbers would not add up or a deadlock would appear; runs are reproducible |
| 19 | Start gate (`CountDownLatch`) in the concurrency tests | All threads begin at the same instant to maximize real contention |
| 20 | The idempotency key is looked up after both accounts are locked | Duplicate requests on the same accounts queue up, and the second one already sees the movement committed by the first; it returns it even when the remaining balance could no longer cover a repeat |
| 21 | A replay answers `200` with `Idempotent-Replayed: true`; the same key with a different request answers `422` | Lets clients tell a replay from a creation; follows the IETF Idempotency-Key header draft |
| 22 | The `UNIQUE` constraint on `idempotency_key` is the last safety net, reported as a conflict | If two requests with the same key but different accounts race, the database still guarantees a single winner |
| 23 | `Idempotency-Key` is optional | Keeps existing clients working; a production bank would make it mandatory for money-moving calls |
| 24 | History query = `UNION ALL` of two index-ordered scans, instead of `WHERE from = ? OR to = ?` | Each side reads only `offset + size` rows already in order; the `OR` form would have to sort the whole history |
| 25 | Composite indexes `(from_account, created_at)` and `(to_account, created_at)` in migration V2 | Serve the `ORDER BY created_at DESC` without a sort; measured in `docs/PERFORMANCE.md` |
| 26 | Offset pagination with a maximum page size of 100 | Simple and familiar; deep pages cost more, so a keyset cursor is the planned improvement |
| 27 | Native query mapped to the `Transaction` entity (not `JdbcTemplate` rows) | Keeps the same date/time conversions as the rest of the persistence layer |
| 28 | springdoc-openapi for the API docs; Actuator exposes only `health` | Interactive documentation with almost no code; no sensitive operational endpoints exposed |
| 29 | JaCoCo report without a coverage gate (for now) | Visibility first; a threshold can be added once the baseline is known |
| 30 | The containerized API lives behind the Compose profile `app` | `docker compose up -d` keeps starting only MySQL for the local development workflow |