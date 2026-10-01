-- Phase 1: users, households, membership, invites. Runs inside the "budget" schema.

CREATE TABLE app_user (
    id           uuid PRIMARY KEY,                 -- Supabase auth user id (JWT "sub")
    email        varchar(320) NOT NULL,
    display_name varchar(120),
    created_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));

CREATE TABLE household (
    id         uuid PRIMARY KEY,
    name       varchar(120) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

-- One household per user (user_id is the primary key). All members have equal rights.
CREATE TABLE household_member (
    user_id      uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    household_id uuid        NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    joined_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_household_member_household ON household_member (household_id);

-- The raw invite token is never stored; only its SHA-256 hash.
CREATE TABLE household_invite (
    id           uuid PRIMARY KEY,
    household_id uuid        NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    token_hash   varchar(64) NOT NULL UNIQUE,
    created_by   uuid        NOT NULL REFERENCES app_user (id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    used_by      uuid REFERENCES app_user (id),
    revoked_at   timestamptz
);
CREATE INDEX idx_household_invite_household ON household_invite (household_id);
