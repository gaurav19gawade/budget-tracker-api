package com.budgettracker.api;

import com.budgettracker.application.InviteManager;
import com.budgettracker.application.InviteRedeemer;
import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.view.CreatedInvite;
import com.budgettracker.domain.Household;
import com.budgettracker.domain.HouseholdInvite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InviteController {

    public record InviteView(UUID id, UUID createdBy, Instant createdAt, Instant expiresAt, String status) {
    }

    public record RedeemRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record JoinedHousehold(UUID id, String name) {
    }

    private final InviteManager invites;
    private final InviteRedeemer redeemer;
    private final Clock clock;

    public InviteController(InviteManager invites, InviteRedeemer redeemer, Clock clock) {
        this.invites = invites;
        this.redeemer = redeemer;
        this.clock = clock;
    }

    /** The response carries the raw token once; it cannot be retrieved again. */
    @PostMapping("/api/households/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedInvite create(HouseholdContext ctx) {
        return invites.create(ctx);
    }

    @GetMapping("/api/households/invites")
    public List<InviteView> list(HouseholdContext ctx) {
        Instant now = clock.instant();
        return invites.list(ctx).stream().map(i -> toView(i, now)).toList();
    }

    @DeleteMapping("/api/households/invites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(HouseholdContext ctx, @PathVariable UUID id) {
        invites.revoke(ctx, id);
    }

    @PostMapping("/api/invites/redeem")
    public JoinedHousehold redeem(AuthenticatedPrincipal principal, @Valid @RequestBody RedeemRequest request) {
        Household household = redeemer.redeem(principal, request.token().trim());
        return new JoinedHousehold(household.id(), household.name());
    }

    private static InviteView toView(HouseholdInvite i, Instant now) {
        return new InviteView(i.id(), i.createdBy(), i.createdAt(), i.expiresAt(), i.statusAt(now).name());
    }
}
