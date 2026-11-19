package com.portfolio.bankapi.transaction;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/accounts/{id}/transactions")
@Tag(name = "History")
public class TransactionHistoryController {

    private final TransactionHistoryService service;

    public TransactionHistoryController(TransactionHistoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Paginated movement history of an account (newest first)",
            description = "Includes movements where the account is the origin or the destination. "
                    + "page starts at 0; size between 1 and 100 (default 20).")
    public PageResponse<TransactionResponse> history(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return service.history(id, page, size);
    }
}
