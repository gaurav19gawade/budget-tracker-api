-- Teller enrollment: one per bank login (one bank login can have several accounts).
-- The raw access token is never stored; only its AES-GCM encrypted form.
CREATE TABLE budget.teller_enrollment (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID         NOT NULL REFERENCES budget.household(id),
    teller_id       VARCHAR(255) NOT NULL,
    institution     VARCHAR(255) NOT NULL,
    encrypted_token TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (teller_id)
);

-- One row per bank account (checking, savings, credit card, etc.) under an enrollment.
-- Soft-deleted on removal so transaction history can still reference the account.
CREATE TABLE budget.bank_account (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id      UUID         NOT NULL REFERENCES budget.household(id),
    enrollment_id     UUID         NOT NULL REFERENCES budget.teller_enrollment(id),
    teller_id         VARCHAR(255) NOT NULL,
    institution       VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    type              VARCHAR(50)  NOT NULL,
    subtype           VARCHAR(50),
    last_four         VARCHAR(4),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'USD',
    balance_available NUMERIC(19,4),
    balance_ledger    NUMERIC(19,4),
    last_synced_at    TIMESTAMPTZ,
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    removed_at        TIMESTAMPTZ,
    UNIQUE (teller_id)
);

CREATE INDEX bank_account_household_status ON budget.bank_account (household_id, status);
