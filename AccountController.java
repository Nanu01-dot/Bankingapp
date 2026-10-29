package com.banking.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<BankAccount> list() {
        return accounts.findAll();
    }

    @GetMapping("/{id}")
    public BankAccount get(@PathVariable UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @GetMapping("/customer/{customerId}")
    public List<BankAccount> byCustomer(@PathVariable UUID customerId) {
        return accounts.findByCustomerId(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccount create(@Valid @RequestBody CreateAccountRequest request) {
        return accounts.save(new BankAccount(request.customerId(), request.currency()));
    }

    @PostMapping("/{id}/deposit")
    @Transactional
    public BankAccount deposit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        BankAccount account = get(id);
        account.deposit(request.amount());
        return account;
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    public BankAccount withdraw(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        BankAccount account = get(id);
        try {
            account.withdraw(request.amount());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        return account;
    }

    public record CreateAccountRequest(
            @NotNull UUID customerId,
            @NotNull @Pattern(regexp = "^[A-Za-z]{3}$") String currency) {
    }

    public record AmountRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }
}
