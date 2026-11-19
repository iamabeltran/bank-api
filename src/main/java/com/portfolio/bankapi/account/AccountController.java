package com.portfolio.bankapi.account;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.bankapi.transaction.TransactionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Capa web: solo traduce HTTP <-> servicio. Cero reglas de negocio aquí. */
@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an account (balance starts at 0)")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = service.create(request.ownerName(), request.currency());
        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account and its balance")
    public AccountResponse get(@PathVariable Long id) {
        return AccountResponse.from(service.get(id));
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit money into an account")
    public TransactionResponse deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return TransactionResponse.from(service.deposit(id, request.amount()));
    }
}
