package com.portfolio.bankapi.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.bankapi.error.InvalidIdempotencyKeyException;
import com.portfolio.bankapi.transaction.TransactionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/transfers")
@Tag(name = "Transfers")
public class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final int MAX_KEY_LENGTH = 64;

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Transfer money between two accounts",
            description = "Optional Idempotency-Key header (max 64 chars): repeating the same request moves the money "
                    + "once and returns 200 with 'Idempotent-Replayed: true'; reusing the key with a different "
                    + "request returns 422.")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH)) {
            throw new InvalidIdempotencyKeyException();
        }

        TransferResult result = service.transfer(
                idempotencyKey, request.fromAccountId(), request.toAccountId(), request.amount());
        TransactionResponse body = TransactionResponse.from(result.transaction());

        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
