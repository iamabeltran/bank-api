package com.portfolio.bankapi.transaction;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.bankapi.account.AccountRepository;
import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.InvalidPaginationException;

@Service
public class TransactionHistoryService {

    static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public TransactionHistoryService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long accountId, int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        long offset = (long) page * size;
        List<TransactionResponse> content = transactions
                .findHistoryPage(accountId, offset + size, size, offset)
                .stream()
                .map(TransactionResponse::from)
                .toList();
        long total = transactions.countHistory(accountId);
        int totalPages = (int) ((total + size - 1) / size);

        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
