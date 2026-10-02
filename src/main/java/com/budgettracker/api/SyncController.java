package com.budgettracker.api;

import com.budgettracker.application.SyncService;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.application.view.TransactionView;
import com.budgettracker.domain.Transaction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncController {

    public record SyncResultView(int newTransactions, int updatedTransactions, Instant syncedAt) {
    }

    private final SyncService syncService;
    private final TransactionRepository transactions;
    private final Clock clock;

    public SyncController(SyncService syncService, TransactionRepository transactions, Clock clock) {
        this.syncService = syncService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Triggers an on-demand sync for the calling household.
     * Returns counts of new and updated transactions.
     */
    @PostMapping("/api/sync")
    public SyncResultView sync(HouseholdContext ctx) {
        SyncService.SyncStats stats = syncService.syncHousehold(ctx.householdId());
        return new SyncResultView(stats.newTransactions(), stats.updatedTransactions(), stats.syncedAt());
    }

    /**
     * Lists transactions for the calling household.
     *
     * @param from start date inclusive (default: first day of current month)
     * @param to   end date inclusive (default: today)
     */
    @GetMapping("/api/transactions")
    public List<TransactionView> list(
            HouseholdContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(30);
        return transactions.findByHouseholdId(ctx.householdId(), start, end)
                .stream()
                .map(SyncController::toView)
                .toList();
    }

    static TransactionView toView(Transaction t) {
        return new TransactionView(
                t.id(), t.accountId(), t.amount(), t.currency(),
                t.description(), t.payee(), t.memo(),
                t.postedDate(), t.transactedAt(),
                t.pending(), t.isInternalTransfer(),
                t.createdAt());
    }
}
