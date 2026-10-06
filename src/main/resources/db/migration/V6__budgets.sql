-- Phase 5: per-category monthly budgets
-- month is always stored as the first day of the month (e.g. 2026-10-01)
-- category_id is nullable: NULL represents the optional overall household budget cap
-- ON DELETE SET NULL so deleting a category zeroes out its budget row rather than dropping history

CREATE TABLE budget.budget (
    id           UUID         PRIMARY KEY,
    household_id UUID         NOT NULL REFERENCES budget.household(id),
    category_id  UUID         REFERENCES budget.category(id) ON DELETE SET NULL,
    month        DATE         NOT NULL,
    amount       NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- NULLS NOT DISTINCT so (household, NULL, month) is also unique
    UNIQUE NULLS NOT DISTINCT (household_id, category_id, month)
);

CREATE INDEX budget_household_month_idx ON budget.budget (household_id, month);
