-- One row per category per household.
-- System categories are seeded on household creation; user-defined categories are created later.
CREATE TABLE budget.category (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID         NOT NULL REFERENCES budget.household(id),
    name         VARCHAR(100) NOT NULL,
    color        VARCHAR(7),          -- hex colour e.g. '#F97316'
    icon         VARCHAR(50),         -- emoji e.g. '🍽'
    is_system    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (household_id, name)
);

-- One rule = one match condition mapping transactions to a category.
-- Rules are evaluated in ascending priority order; the first match wins.
-- Deleting a category cascades to its rules.
CREATE TABLE budget.category_rule (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID         NOT NULL REFERENCES budget.household(id),
    category_id  UUID         NOT NULL REFERENCES budget.category(id) ON DELETE CASCADE,
    priority     INT          NOT NULL DEFAULT 0,
    match_field  VARCHAR(30)  NOT NULL,
    -- PAYEE_CONTAINS | DESCRIPTION_CONTAINS | AMOUNT_GTE | AMOUNT_LTE | ACCOUNT_ID
    match_value  VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Add category tracking to transactions.
-- ON DELETE SET NULL: removing a category un-categorises its transactions rather than deleting them.
ALTER TABLE budget.transaction
    ADD COLUMN category_id       UUID    REFERENCES budget.category(id) ON DELETE SET NULL,
    ADD COLUMN category_override BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX category_rule_household_priority ON budget.category_rule (household_id, priority);
CREATE INDEX transaction_category             ON budget.transaction     (category_id);
