# Budget Tracker: Phased Plan

## Locked decisions
- **Bank data:** SimpleFin Bridge (replaced Teller which shut down). Behind a `BankDataProvider` interface so the provider can be swapped again if needed. ~24 requests/day limit on beta tier; no push/webhooks — polling only.
- **Backend:** Java 21, Spring Boot 3.5, Maven, own repo, deployed on Railway.
- **Frontend:** Next.js + TypeScript + Tailwind, own repo, deployed on Vercel.
- **DB:** Supabase Postgres. Schema is owned by the backend via Flyway migrations.
- **Auth:** Supabase Auth. Frontend logs in; backend validates the Supabase JWT (JWKS) as a resource server.
- **Users:** you + partner/family, so a **household** model with shared accounts, budgets and categories.
- **Categories:** provider category auto-mapped, plus a user-defined **rules engine**.
- **Budgets:** per-category monthly budgets plus an overall monthly total.
- **Repos:** two GitHub repos (`budget-tracker-api`, `budget-tracker-web`).

## Architecture sketch
- Backend layers: `api` (controllers/DTOs) -> `application` (use cases) -> `domain` (entities, rules) <- `infrastructure` (JPA, SimpleFin client, scheduler). Dependencies point inward (DIP).
- Key abstractions (SOLID):
  - `BankDataProvider` (SimpleFinBankDataProvider, swappable) - OCP/DIP
  - `CategorizationRule` strategies chained by a `Categorizer` - OCP
  - `SyncService` split from `SyncScheduler` and `SyncJobRunner` - SRP
  - Small ports per use case (ISP)
- Sync design: a `sync_jobs` table acts as a DB-backed queue. The nightly scheduler (ShedLock so only one instance fires) and the on-demand API both enqueue jobs. Workers process them idempotently, upserting transactions by provider transaction id. This scales horizontally on Railway.
- Security: SimpleFin access URLs encrypted at rest (AES-GCM, key in Railway env). Every query scoped by `household_id`. Supabase RLS as defense in depth.
- Money: `NUMERIC(19,4)` + currency code. Never floats.

## Phase 0: Foundations — DONE
- [x] Spring Boot skeleton (Java 21, Maven), layered packages, health endpoint, Flyway baseline, Testcontainers smoke test, Dockerfile.
- [x] Next.js skeleton, Tailwind, dashboard shell. Lint, typecheck and build pass.
- [x] GitHub Actions CI workflows in both repos.
- [x] `.env.example` in both repos documenting every variable.
- [x] Both apps deployed (Railway + Vercel), health check reachable, CI green.

## Phase 1: Auth + households — DONE
- [x] Flyway `V2`: `app_user`, `household`, `household_member`, `household_invite`
- [x] Spring OAuth2 resource server; Supabase JWT validation via JWKS (ES256)
- [x] `CurrentUserProvider` + `HouseholdContext` argument resolver; JIT user provisioning
- [x] Endpoints: `GET /api/me`, `POST /api/households`, `POST /api/households/invites`, `GET /api/households/invites`, `DELETE /api/households/invites/{id}`, `POST /api/invites/redeem`
- [x] Local seeder for dev user/household (no auth in `local` profile)
- [x] Integration tests: no token -> 401, household isolation, invite single-use/expired/revoked, BOOTSTRAP_OWNER_USER_ID gating, token stored hashed
- [x] Frontend: sign-in/sign-up pages, session refresh, route protection, API client with Bearer token
- [x] `/join?token=...` page; household page (members list, invite create/copy/revoke)
- [x] Deployed and working end-to-end (email confirmation, CORS, owner bootstrap)

## Phase 2: Connect/remove accounts (SimpleFin Bridge) — DONE
- [x] Flyway `V3`: `teller_enrollment`, `bank_account`
- [x] `BankDataProvider` port — `claim(setupToken)` + `fetchAccounts(accessCredential)` (includes balances)
- [x] `TokenEncryptionService` — AES-256-GCM, key from Railway env
- [x] `SimpleFinClient` — claim access URL, fetch accounts with Basic auth from embedded credentials
- [x] `EnrollmentService` — connect (idempotent via SHA-256 fingerprint of access URL), disconnect (soft-delete; removes enrollment when last account gone)
- [x] Endpoints: `POST /api/simplefin/connections`, `GET /api/accounts`, `DELETE /api/accounts/{id}`
- [x] 10 integration tests covering auth, household isolation, idempotency, remove
- [x] Frontend: accounts page with two-step connect flow (link to SimpleFin + setup token textarea)
- [x] Deployed and working (Chase connected and visible)

**Notes on SimpleFin Bridge:**
- ~24 requests/day limit (roughly 1/hour) — be deliberate about when `/accounts` is called
- Balances are only as fresh as the last sync; no webhooks
- When adding balance refresh: enforce a minimum 1-hour cooldown server-side using `last_synced_at`

## Phase 3: Transaction sync engine
- [ ] Flyway `V4`: `transactions` (provider_id UNIQUE per household, amount, date, description, pending, category_hint, category_id, is_internal_transfer, transfer_group_id)
- [ ] `sync_jobs` table — DB-backed queue; nightly scheduler (ShedLock) + on-demand endpoint
- [ ] `SyncWorker` — fetch transactions from SimpleFin `/accounts?start-date=...`, upsert by provider id (idempotent), handle pending -> posted transitions
- [ ] Sync respects SimpleFin's 24 req/day limit: deduplicate calls, one job per connection not per account
- [ ] On-demand sync endpoint `POST /api/sync` + "Sync now" button with last-synced timestamp
- [ ] Nightly scheduled sync at 2:00 AM America/New_York (ShedLock so only one Railway instance fires)
- [ ] Handle "disconnected / re-auth needed" state — show error in UI prompting reconnect
- Verify: duplicate-safe re-runs; nightly job fires once; failure visible in UI

## Phase 4: Categories + rules
- [ ] Default category set; user-defined categories (name, color, icon); delete with reassignment
- [ ] Map SimpleFin category hint -> your category on import
- [ ] Rules engine (merchant/description contains, amount range, account); priority order; apply to new and past transactions
- [ ] Manual override on a transaction wins over rules
- Verify: rule precedence tests; re-run rules is idempotent

## Phase 5: Budgets
- [ ] Per-category monthly budget + overall monthly total
- [ ] Budget vs actual per month; progress bars; over-budget state
- [ ] Copy last month's budget
- Verify: totals match transaction sums for the month

## Phase 6: Analytics dashboard (home screen)
- [ ] Top expenses of the month
- [ ] Total money in; total money out
- [ ] Budget progress summary, spend by category chart
- [ ] Efficient aggregate queries + indexes (`household_id`, `posted_date`, `category_id`)
- Verify: numbers reconcile to raw transactions; transfers/card payments not double-counted

## Phase 7: Hardening and production
- [ ] Observability: structured logs, metrics, error tracking, sync dashboards/alerts
- [ ] Rate limiting, input validation, security review, dependency scanning
- [ ] Load test aggregate queries; add caching only if measured
- [ ] Backups/restore drill on Supabase; staging vs prod separation
- [ ] Manual balance refresh with 1-hour cooldown (SimpleFin rate limit)

## Resolved decisions
- Internal transfers and credit card payments are excluded from money in/out (strategy below).
- Removing an account: its transactions leave the main dataset and move to an analytics table.
- Nightly sync at 2:00 AM America/New_York.
- All household members see everything (no private accounts).
- USD only (single currency, no FX handling).
- Unused budget rolls over to the next month.

## Transfer / credit card payment strategy (Phase 3 detection, Phase 6 consumption)
Principle: never delete or hide data. Flag and link it, so every decision is reversible and auditable.
1. **Hints (low confidence):** provider type/category and description patterns (e.g. "payment", "autopay", "transfer"). Raises confidence only; never decides alone.
2. **Pair matching (the core rule):** for each new posted transaction, look in the *other* household accounts for one with the opposite sign, same absolute amount, within +/-3 days. Exactly one candidate -> auto-link both as a transfer pair (shared `transfer_group_id`, `is_internal_transfer = true`). Several candidates -> "needs review" queue.
3. **Only one side is connected:** real money leaving tracked accounts, stays as outflow in system category "Card Payment (unlinked)". Linking the other account later re-runs matching and fixes history.
4. **Pending -> posted:** match on posted transactions only; re-run when a pending item posts.
5. **User override:** mark/unmark any transaction as a transfer; manual overrides always beat auto-matching.
6. **Consumption:** all analytics, budgets and "money in/out" queries filter `is_internal_transfer = false`.
7. Tests: exact pair, ambiguous pair, date drift, one-sided, refund vs transfer, re-run idempotency.

## Account removal design (Phase 2 — implemented, Phase 3 extension needed)
- Access credentials are per *connection* (one SimpleFin setup, possibly several accounts). Removing one account soft-deletes it; the connection (enrollment) is only deleted when its last active account is removed.
- Phase 3 extension: on removal, copy the account's transactions into `analytics_transactions` (denormalized), delete them from `transactions`, mark account `removed`. Transfer links involving that account are converted per strategy item 3.

## Budget rollover design (Phase 5)
- Per category: `available(month) = budget(month) + carryover(prev month)`, where `carryover = available(prev) - spent(prev)`.
- Computed on read from transactions + budgets (no stored running balance), so edits to past transactions stay consistent.

## Remaining open questions
1. Year-start reset of rollover balances: not answered yet. Plan currently assumes **no reset** (balances carry across years). Say if you want a January reset.

## Final decisions
- Overspending carries forward as a negative balance and reduces next month's available amount.
- Dashboard and budget history include data from removed accounts (read from `analytics_transactions` unioned with `transactions` via a single view/query layer, so callers don't care where rows live).
- Household members have equal rights (connect/remove accounts, edit budgets, categories, rules).
