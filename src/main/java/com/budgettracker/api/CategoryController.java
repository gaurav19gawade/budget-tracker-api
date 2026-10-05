package com.budgettracker.api;

import com.budgettracker.application.CategoryService;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.view.CategoryRuleView;
import com.budgettracker.application.view.CategoryView;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {

    // ---- request bodies ---------------------------------------------------------

    public record CreateCategoryRequest(@NotBlank String name, String color, String icon) {}

    public record DeleteCategoryRequest(UUID reassignTo) {}

    public record SetTransactionCategoryRequest(UUID categoryId) {}

    public record CreateRuleRequest(
            @NotNull UUID categoryId,
            int priority,
            @NotNull CategoryRule.MatchField matchField,
            @NotBlank String matchValue) {}

    public record UpdateRulePriorityRequest(int priority) {}

    public record ApplyRulesResult(int categorized) {}

    // ---- wiring -----------------------------------------------------------------

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    // ---- categories -------------------------------------------------------------

    @GetMapping("/api/categories")
    public List<CategoryView> listCategories(HouseholdContext ctx) {
        return service.list(ctx).stream().map(CategoryController::toView).toList();
    }

    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(HouseholdContext ctx,
                                       @Valid @RequestBody CreateCategoryRequest req) {
        return toView(service.create(ctx, req.name(), req.color(), req.icon()));
    }

    @DeleteMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(HouseholdContext ctx,
                               @PathVariable UUID id,
                               @RequestBody(required = false) DeleteCategoryRequest req) {
        service.delete(ctx, id, req != null ? req.reassignTo() : null);
    }

    // ---- transaction category override ------------------------------------------

    @PatchMapping("/api/transactions/{id}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setTransactionCategory(HouseholdContext ctx,
                                       @PathVariable UUID id,
                                       @RequestBody SetTransactionCategoryRequest req) {
        service.setTransactionCategory(ctx, id, req.categoryId());
    }

    // ---- rules ------------------------------------------------------------------

    @GetMapping("/api/category-rules")
    public List<CategoryRuleView> listRules(HouseholdContext ctx) {
        return service.listRules(ctx).stream().map(CategoryController::toRuleView).toList();
    }

    @PostMapping("/api/category-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryRuleView createRule(HouseholdContext ctx,
                                       @Valid @RequestBody CreateRuleRequest req) {
        return toRuleView(service.createRule(
                ctx, req.categoryId(), req.priority(), req.matchField(), req.matchValue()));
    }

    @DeleteMapping("/api/category-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(HouseholdContext ctx, @PathVariable UUID id) {
        service.deleteRule(ctx, id);
    }

    @PatchMapping("/api/category-rules/{id}/priority")
    public CategoryRuleView updateRulePriority(HouseholdContext ctx,
                                               @PathVariable UUID id,
                                               @RequestBody UpdateRulePriorityRequest req) {
        return toRuleView(service.updateRulePriority(ctx, id, req.priority()));
    }

    /** Re-applies all rules to every non-overridden transaction. */
    @PostMapping("/api/category-rules/apply")
    public ApplyRulesResult applyRules(HouseholdContext ctx) {
        return new ApplyRulesResult(service.applyRules(ctx));
    }

    // ---- mappers ----------------------------------------------------------------

    static CategoryView toView(Category c) {
        return new CategoryView(c.id(), c.name(), c.color(), c.icon(), c.isSystem(), c.createdAt());
    }

    static CategoryRuleView toRuleView(CategoryRule r) {
        return new CategoryRuleView(
                r.id(), r.categoryId(), r.priority(), r.matchField(), r.matchValue(), r.createdAt());
    }
}
