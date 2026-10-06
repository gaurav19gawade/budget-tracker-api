package com.budgettracker.application;

import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.CategoryRepository;
import com.budgettracker.application.port.CategoryRuleRepository;
import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryRule;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    /**
     * Default categories seeded for every new household.
     * Each entry: { name, color (hex), icon (emoji) }
     */
    private static final List<Object[]> DEFAULTS = List.of(
            new Object[]{"Food & Dining",     "#F97316", "\uD83C\uDF7D"},  // 🍽
            new Object[]{"Groceries",         "#22C55E", "\uD83D\uDED2"},  // 🛒
            new Object[]{"Shopping",          "#3B82F6", "\uD83D\uDECD"},  // 🛍
            new Object[]{"Transportation",    "#8B5CF6", "\uD83D\uDE97"},  // 🚗
            new Object[]{"Bills & Utilities", "#EAB308", "\uD83D\uDCCB"},  // 📋
            new Object[]{"Entertainment",     "#EC4899", "\uD83C\uDFAC"},  // 🎬
            new Object[]{"Health",            "#EF4444", "\u2764\uFE0F"},  // ❤️
            new Object[]{"Travel",            "#14B8A6", "\u2708\uFE0F"},  // ✈️
            new Object[]{"Personal Care",     "#F472B6", "\uD83D\uDC86"},  // 💆
            new Object[]{"Home",              "#A78BFA", "\uD83C\uDFE0"},  // 🏠
            new Object[]{"Income",            "#10B981", "\uD83D\uDCB0"},  // 💰
            new Object[]{"Gifts",             "#FB923C", "\uD83C\uDF81"},  // 🎁
            new Object[]{"Education",         "#60A5FA", "\uD83D\uDCDA"},  // 📚
            new Object[]{"Uncategorized",     "#9CA3AF", "\uD83D\uDCE6"}   // 📦
    );

    private final CategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final TransactionRepository transactions;
    private final Categorizer categorizer;
    private final Clock clock;

    public CategoryService(CategoryRepository categories,
                           CategoryRuleRepository rules,
                           TransactionRepository transactions,
                           Categorizer categorizer,
                           Clock clock) {
        this.categories = categories;
        this.rules = rules;
        this.transactions = transactions;
        this.categorizer = categorizer;
        this.clock = clock;
    }

    // ---- Default seeding --------------------------------------------------------

    /**
     * Seeds default categories for the given household if none exist yet.
     * Idempotent — safe to call on every startup or household creation.
     */
    public void seedDefaultsIfNone(UUID householdId) {
        if (categories.existsByHouseholdId(householdId)) return;
        Instant now = clock.instant();
        for (Object[] d : DEFAULTS) {
            categories.save(new Category(
                    UUID.randomUUID(), householdId,
                    (String) d[0], (String) d[1], (String) d[2],
                    true, now));
        }
    }

    // ---- Categories CRUD --------------------------------------------------------

    public List<Category> list(HouseholdContext ctx) {
        return categories.findByHouseholdId(ctx.householdId());
    }

    public Category create(HouseholdContext ctx, String name, String color, String icon) {
        return categories.save(new Category(
                UUID.randomUUID(), ctx.householdId(),
                name, color, icon, false, clock.instant()));
    }

    @Transactional
    public void delete(HouseholdContext ctx, UUID categoryId, UUID reassignToId) {
        Category cat = requireOwned(ctx, categoryId);
        if (reassignToId != null) {
            requireOwned(ctx, reassignToId);
            transactions.reassignCategory(categoryId, reassignToId);
        }
        // transactions with no reassignment target will have category_id set to NULL
        // by the ON DELETE SET NULL FK constraint when the category row is deleted.
        categories.deleteById(cat.id()); // cascades to category_rule rows
    }

    // ---- Transaction category override ------------------------------------------

    public void setTransactionCategory(HouseholdContext ctx, UUID transactionId, UUID categoryId) {
        if (categoryId != null) requireOwned(ctx, categoryId);
        transactions.updateCategory(transactionId, categoryId, true);
    }

    // ---- Rules ------------------------------------------------------------------

    public List<CategoryRule> listRules(HouseholdContext ctx) {
        return rules.findByHouseholdId(ctx.householdId());
    }

    @Transactional
    public CategoryRule createRule(HouseholdContext ctx, UUID categoryId, int priority,
                                   CategoryRule.MatchField matchField, String matchValue) {
        requireOwned(ctx, categoryId);
        CategoryRule rule = rules.save(new CategoryRule(
                UUID.randomUUID(), ctx.householdId(), categoryId,
                priority, matchField, matchValue, clock.instant()));
        applyRules(ctx);
        return rule;
    }

    public void deleteRule(HouseholdContext ctx, UUID ruleId) {
        CategoryRule rule = rules.findById(ruleId)
                .orElseThrow(() -> new NotFoundException("Rule not found."));
        if (!rule.householdId().equals(ctx.householdId()))
            throw new NotFoundException("Rule not found.");
        rules.deleteById(ruleId);
    }

    @Transactional
    public CategoryRule updateRulePriority(HouseholdContext ctx, UUID ruleId, int newPriority) {
        CategoryRule rule = rules.findById(ruleId)
                .orElseThrow(() -> new NotFoundException("Rule not found."));
        if (!rule.householdId().equals(ctx.householdId()))
            throw new NotFoundException("Rule not found.");
        CategoryRule updated = rules.save(new CategoryRule(
                rule.id(), rule.householdId(), rule.categoryId(),
                newPriority, rule.matchField(), rule.matchValue(), rule.createdAt()));
        applyRules(ctx);
        return updated;
    }

    /**
     * Re-runs all household rules against every non-overridden transaction.
     * Transactions where the user manually set a category are skipped.
     * Returns the number of transactions that were (re-)categorised.
     */
    @Transactional
    public int applyRules(HouseholdContext ctx) {
        List<CategoryRule> ruleList = rules.findByHouseholdId(ctx.householdId());
        List<Transaction> txs = transactions.findAllByHouseholdId(ctx.householdId());
        int count = 0;
        for (Transaction tx : txs) {
            if (tx.categoryOverride()) continue;
            UUID categoryId = categorizer.categorize(
                    tx.payee(), tx.description(), tx.amount(), tx.accountId(), ruleList);
            transactions.updateCategory(tx.id(), categoryId, false);
            count++;
        }
        return count;
    }

    // ---- helpers ----------------------------------------------------------------

    private Category requireOwned(HouseholdContext ctx, UUID categoryId) {
        Category cat = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found."));
        if (!cat.householdId().equals(ctx.householdId()))
            throw new NotFoundException("Category not found.");
        return cat;
    }
}
