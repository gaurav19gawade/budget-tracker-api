package com.budgettracker.application;

import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.BudgetRepository;
import com.budgettracker.application.port.CategoryRepository;
import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.application.view.BudgetSummaryEntry;
import com.budgettracker.domain.Budget;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Clock clock;

    public BudgetService(BudgetRepository budgets,
                         CategoryRepository categories,
                         TransactionRepository transactions,
                         Clock clock) {
        this.budgets = budgets;
        this.categories = categories;
        this.transactions = transactions;
        this.clock = clock;
    }

    // ---- CRUD -------------------------------------------------------------------

    /** Upserts a budget for the given category + month. Pass {@code categoryId=null} for the overall cap. */
    @Transactional
    public Budget setBudget(HouseholdContext ctx, UUID categoryId, YearMonth month, BigDecimal amount) {
        if (categoryId != null) requireOwnedCategory(ctx, categoryId);
        LocalDate monthStart = month.atDay(1);
        Optional<Budget> existing = budgets.findByHouseholdIdAndCategoryIdAndMonth(
                ctx.householdId(), categoryId, monthStart);
        UUID id = existing.map(Budget::id).orElseGet(UUID::randomUUID);
        Instant createdAt = existing.map(Budget::createdAt).orElseGet(clock::instant);
        return budgets.save(new Budget(id, ctx.householdId(), categoryId, monthStart, amount, createdAt));
    }

    @Transactional
    public void deleteBudget(HouseholdContext ctx, UUID categoryId, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        Budget budget = budgets.findByHouseholdIdAndCategoryIdAndMonth(
                        ctx.householdId(), categoryId, monthStart)
                .orElseThrow(() -> new NotFoundException("Budget not found."));
        budgets.deleteById(budget.id());
    }

    // ---- Summary ----------------------------------------------------------------

    /**
     * Returns one entry per category that has either a budget or transactions in the month,
     * plus a totals entry with {@code categoryId=null}.
     */
    public List<BudgetSummaryEntry> getSummary(HouseholdContext ctx, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        // Budget rows for this month
        Map<UUID, Budget> budgetMap = budgets.findByHouseholdIdAndMonth(ctx.householdId(), from)
                .stream()
                .filter(b -> b.categoryId() != null)
                .collect(Collectors.toMap(Budget::categoryId, b -> b));

        // All categories for the household (for names/colors/icons)
        Map<UUID, Category> categoryMap = categories.findByHouseholdId(ctx.householdId())
                .stream()
                .collect(Collectors.toMap(Category::id, c -> c));

        // Aggregate transaction sums by category
        Map<UUID, BigDecimal[]> spendMap = buildSpendMap(ctx.householdId(), from, to);

        // Union of all category IDs that appear in either budgets or transactions
        java.util.Set<UUID> catIds = new java.util.HashSet<>();
        catIds.addAll(budgetMap.keySet());
        catIds.addAll(spendMap.keySet().stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet()));

        List<BudgetSummaryEntry> entries = new ArrayList<>();
        BigDecimal totalBudgeted = BigDecimal.ZERO;
        BigDecimal totalSpent    = BigDecimal.ZERO;
        BigDecimal totalIncome   = BigDecimal.ZERO;

        for (UUID catId : catIds) {
            Category cat = categoryMap.get(catId);
            String name  = cat != null ? cat.name()  : "Unknown";
            String color = cat != null ? cat.color() : null;
            String icon  = cat != null ? cat.icon()  : null;

            BigDecimal budgeted = budgetMap.containsKey(catId)
                    ? budgetMap.get(catId).amount() : BigDecimal.ZERO;
            BigDecimal[] sums = spendMap.getOrDefault(catId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal spent  = sums[0]; // absolute sum of negative txns
            BigDecimal income = sums[1]; // sum of positive txns

            entries.add(new BudgetSummaryEntry(catId, name, color, icon, budgeted, spent, income,
                    budgeted.subtract(spent)));

            totalBudgeted = totalBudgeted.add(budgeted);
            totalSpent    = totalSpent.add(spent);
            totalIncome   = totalIncome.add(income);
        }

        // Sort: budgeted categories first (by name), then unbudgeted
        entries.sort(java.util.Comparator
                .<BudgetSummaryEntry, Boolean>comparing(e -> e.budgeted().compareTo(BigDecimal.ZERO) == 0)
                .thenComparing(BudgetSummaryEntry::categoryName));

        // Totals row (categoryId = null)
        entries.add(new BudgetSummaryEntry(null, "Total", null, null,
                totalBudgeted, totalSpent, totalIncome, totalBudgeted.subtract(totalSpent)));

        return entries;
    }

    // ---- Copy previous month ----------------------------------------------------

    /**
     * Copies all category budgets from the previous calendar month into {@code month}.
     * Existing budgets for {@code month} are overwritten.
     * Returns the number of budgets copied.
     */
    @Transactional
    public int copyFromPreviousMonth(HouseholdContext ctx, YearMonth month) {
        YearMonth prevMonth = month.minusMonths(1);
        LocalDate prevStart = prevMonth.atDay(1);
        List<Budget> prev = budgets.findByHouseholdIdAndMonth(ctx.householdId(), prevStart)
                .stream()
                .filter(b -> b.categoryId() != null)
                .toList();
        Instant now = clock.instant();
        LocalDate thisStart = month.atDay(1);
        int count = 0;
        for (Budget source : prev) {
            Optional<Budget> existing = budgets.findByHouseholdIdAndCategoryIdAndMonth(
                    ctx.householdId(), source.categoryId(), thisStart);
            UUID id = existing.map(Budget::id).orElseGet(UUID::randomUUID);
            Instant createdAt = existing.map(Budget::createdAt).orElse(now);
            budgets.save(new Budget(id, ctx.householdId(), source.categoryId(),
                    thisStart, source.amount(), createdAt));
            count++;
        }
        return count;
    }

    // ---- helpers ----------------------------------------------------------------

    /**
     * Builds a map from categoryId → [spent (abs negative), income (positive)].
     * Null key covers uncategorized transactions.
     */
    private Map<UUID, BigDecimal[]> buildSpendMap(UUID householdId, LocalDate from, LocalDate to) {
        List<Object[]> rows = transactions.sumByCategoryForPeriod(householdId, from, to);
        Map<UUID, BigDecimal[]> map = new java.util.HashMap<>();
        for (Object[] row : rows) {
            UUID catId = (UUID) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            BigDecimal[] buckets = map.computeIfAbsent(catId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (sum.compareTo(BigDecimal.ZERO) < 0) {
                buckets[0] = buckets[0].add(sum.negate()); // spent
            } else {
                buckets[1] = buckets[1].add(sum);           // income
            }
        }
        return map;
    }

    private void requireOwnedCategory(HouseholdContext ctx, UUID categoryId) {
        Category cat = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found."));
        if (!cat.householdId().equals(ctx.householdId()))
            throw new NotFoundException("Category not found.");
    }
}
