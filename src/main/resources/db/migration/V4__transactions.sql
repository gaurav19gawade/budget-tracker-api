-- One row per imported bank transaction.
-- provider_id is the ID from SimpleFin; UNIQUE per household to enable idempotent upserts.
CREATE TABLE budget.transaction (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id         UUID          NOT NULL REFERENCES budget.household(id),
    account_id           UUID          NOT NULL REFERENCES budget.bank_account(id),
    provider_id          VARCHAR(255)  NOT NULL,
    amount               NUMERIC(19,4) NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'USD',
    description          TEXT,
    payee                VARCHAR(500),
    memo                 TEXT,
    posted_date          DATE,
    transacted_at        DATE,
    pending              BOOLEAN       NOT NULL DEFAULT false,
    is_internal_transfer BOOLEAN       NOT NULL DEFAULT false,
    transfer_group_id    UUID,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (household_id, provider_id)
);

CREATE INDEX transaction_household_posted ON budget.transaction (household_id, posted_date DESC NULLS LAST);
CREATE INDEX transaction_account ON budget.transaction (account_id);

-- ShedLock table for distributed scheduler locking (nightly sync).
CREATE TABLE budget.shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
