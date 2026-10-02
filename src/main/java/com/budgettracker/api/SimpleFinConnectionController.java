package com.budgettracker.api;

import com.budgettracker.application.EnrollmentService;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.view.AccountView;
import com.budgettracker.domain.BankAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simplefin/connections")
public class SimpleFinConnectionController {

    public record ConnectRequest(@NotBlank String setupToken) {
    }

    private final EnrollmentService enrollmentService;

    public SimpleFinConnectionController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<AccountView> connect(HouseholdContext ctx,
                                     @Valid @RequestBody ConnectRequest request) {
        List<BankAccount> connected = enrollmentService.connect(ctx, request.setupToken());
        return connected.stream().map(AccountController::toView).toList();
    }
}
