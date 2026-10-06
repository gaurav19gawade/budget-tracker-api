package com.budgettracker.api;

import com.budgettracker.application.BudgetService;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.view.BudgetSummaryEntry;
import com.budgettracker.domain.Budget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BudgetController {

    public record SetBudgetRequest(@NotNull BigDecimal amount) {}

    public record BudgetView(UUID id, UUID categoryId, String month, BigDecimal amount) {}

    public record CopyResult(int copied) {}

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    /** Monthly budget summary — one row per category + a totals row. */
    @GetMapping("/api/budgets")
    public List<BudgetSummaryEntry> getSummary(
            HouseholdContext ctx,
            @RequestParam(required = false) String month) {
        return service.getSummary(ctx, parseMonth(month));
    }

    /** Upserts the budget for a specific category + month. */
    @PutMapping("/api/budgets/{categoryId}")
    public BudgetView setBudget(
            HouseholdContext ctx,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody SetBudgetRequest req) {
        Budget b = service.setBudget(ctx, categoryId, parseMonth(month), req.amount());
        return toView(b);
    }

    /** Removes the budget for a specific category + month. */
    @DeleteMapping("/api/budgets/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(
            HouseholdContext ctx,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String month) {
        service.deleteBudget(ctx, categoryId, parseMonth(month));
    }

    /** Copies all category budgets from the previous calendar month. */
    @PostMapping("/api/budgets/copy-previous")
    public CopyResult copyFromPreviousMonth(
            HouseholdContext ctx,
            @RequestParam(required = false) String month) {
        return new CopyResult(service.copyFromPreviousMonth(ctx, parseMonth(month)));
    }

    // ---- helpers ----------------------------------------------------------------

    /** Parses "YYYY-MM" param; defaults to current month if null. */
    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid month format; expected YYYY-MM.");
        }
    }

    private static BudgetView toView(Budget b) {
        YearMonth ym = YearMonth.from(b.month());
        return new BudgetView(b.id(), b.categoryId(), ym.toString(), b.amount());
    }
}
