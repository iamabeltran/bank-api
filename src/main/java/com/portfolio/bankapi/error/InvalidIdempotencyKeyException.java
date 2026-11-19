package com.portfolio.bankapi.error;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must be 1 to 64 characters and not blank");
    }
}
