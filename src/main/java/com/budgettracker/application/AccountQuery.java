package com.budgettracker.application;

import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.BankAccountRepository;
import com.budgettracker.domain.BankAccount;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountQuery {

    private final BankAccountRepository accounts;

    public AccountQuery(BankAccountRepository accounts) {
        this.accounts = accounts;
    }

    public List<BankAccount> list(HouseholdContext ctx) {
        return accounts.findActiveByHouseholdId(ctx.householdId());
    }
}
