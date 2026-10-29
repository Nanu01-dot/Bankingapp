package com.banking.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<BankAccount, UUID> {
    List<BankAccount> findByCustomerId(UUID customerId);
}
