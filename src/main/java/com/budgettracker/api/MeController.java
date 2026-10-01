package com.budgettracker.api;

import com.budgettracker.application.MeQuery;
import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.view.MeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeQuery meQuery;

    public MeController(MeQuery meQuery) {
        this.meQuery = meQuery;
    }

    @GetMapping
    public MeView me(AuthenticatedPrincipal principal) {
        return meQuery.get(principal);
    }
}
