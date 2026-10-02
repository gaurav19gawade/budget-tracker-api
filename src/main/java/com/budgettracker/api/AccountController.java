package com.budgettracker.api;

import com.budgettracker.application.AccountQuery;
import com.budgettracker.application.EnrollmentService;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.view.AccountView;
import com.budgettracker.domain.BankAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountQuery query;
    private final EnrollmentService enrollmentService;

    public AccountController(AccountQuery query, EnrollmentService enrollmentService) {
        this.query = query;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public List<AccountView> list(HouseholdContext ctx) {
        return query.list(ctx).stream().map(AccountController::toView).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(HouseholdContext ctx, @PathVariable UUID id) {
        enrollmentService.disconnect(ctx, id);
    }

    static AccountView toView(BankAccount a) {
        return new AccountView(a.id(), a.institution(), a.name(), a.type(), a.subtype(),
                a.lastFour(), a.currency(), a.balanceAvailable(), a.balanceLedger(), a.lastSyncedAt());
    }
}
