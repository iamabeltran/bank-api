# Performance: paginated history

## The query

`GET /accounts/{id}/transactions` returns the movements of an account (as origin or destination), newest
first. A plain `WHERE from_account = ? OR to_account = ? ORDER BY created_at DESC LIMIT n` forces the database to
find and sort the account's whole history before returning one page. Instead, the repository
(`TransactionRepository.findHistoryPage`) takes the newest `offset + size` rows from each side with an
index-ordered scan and merges the two small lists:

```sql
SELECT h.* FROM (
  (SELECT * FROM transactions WHERE from_account = :id ORDER BY created_at DESC, id DESC LIMIT :window)
  UNION ALL
  (SELECT * FROM transactions WHERE to_account = :id ORDER BY created_at DESC, id DESC LIMIT :window)
) AS h
ORDER BY h.created_at DESC, h.id DESC
LIMIT :size OFFSET :offset
```

Migration `V2__add_transaction_history_indexes.sql` adds `(from_account, created_at)` and
`(to_account, created_at)`, so each side reads only `window` rows, already in order, without a sort step.

## How to measure it yourself

The script builds a scratch table with 500,000 synthetic movements (account 1 takes part in all of them, the
worst case), runs `EXPLAIN ANALYZE` with only single-column indexes ("before"), adds the composite indexes and runs
it again ("after"). It never touches the real `transactions` table.

```powershell
docker compose up -d
Get-Content docs\performance\measure.sql | docker exec -i bank-mysql mysql -ubank -pbank bank
```

Read the `actual time=...` of the top node and the `rows=` of the scan nodes in each `EXPLAIN ANALYZE` block.

## Results (fill in with your own run)

| Query | Before: time / rows read | After: time / rows read |
|---|---|---|
| Page 0, size 20 | _TBD_ | _TBD_ |
| Page 5, size 20 (offset 100) | _TBD_ | _TBD_ |

Environment: _MySQL 8.4 in Docker, your CPU/RAM, date_.

> Do not quote these numbers on a CV until you have measured them on your machine.

## Known trade-offs

- Offset pagination: the window grows with the page number, so very deep pages cost more. Page size is capped at
  100. A keyset ("seek") cursor on `(created_at, id)` would remove that cost and is the natural next step.
- Synthetic data: the distribution (one account in every row) is intentionally adversarial; real workloads are
  usually kinder.