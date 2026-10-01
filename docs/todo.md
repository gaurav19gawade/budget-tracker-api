# Budget Tracker: Phased Plan (DRAFT, awaiting approval)

## Locked decisions
- **Bank data:** Teller (sandbox for UI/flow, development for real data, 100-enrollment cap; production later). Behind a `BankDataProvider` interface so the provider can be swapped.
- **Backend:** Java 21, Spring Boot 3, Maven, own repo, deployed on Railway.
- **Frontend:** Next.js + TypeScript + Tailwind (+ shadcn/ui, Recharts, TanStack Query), own repo, deployed on Vercel.
- **DB:** Supabase Postgres. Schema is owned by the backend via Flyway migrations.
- **Auth:** Supabase Auth. Frontend logs in; backend validates the Supabase JWT (JWKS) as a resource server.
- **Users:** you + partner/family, so a **household** model with shared accounts, budgets and categories.
- **Categories:** Teller category auto-mapped, plus a user-defined **rules engine**.
- **Budgets:** per-category monthly budgets plus an overall monthly total.
- **Repos:** two GitHub repos (`budget-tracker-api`, `budget-tracker-web`).

## Architecture sketch
- Backend layers: `api` (controllers/DTOs) -> `application` (use cases) -> `domain` (entities, rules) <- `infrastructure` (JPA, Teller client, scheduler). Dependencies point inward (DIP).
- Key abstractions (SOLID):
  - `BankDataProvider` (TellerProvider, later others) - OCP/DIP
  - `CategorizationRule` strategies chained by a `Categorizer` - OCP
  - `SyncService` split from `SyncScheduler` and `SyncJobRunner` - SRP
  - Small ports per use case (ISP)
- Sync design: a `sync_jobs` table acts as a DB-backed queue. The nightly scheduler (ShedLock so only one instance fires) and the on-demand API both enqueue jobs. Workers process them idempotently, upserting transactions by Teller transaction id. This scales horizontally on Railway.
- Security: Teller access tokens encrypted at rest (AES-GCM, key in Railway env). Teller mTLS client cert/key loaded from Railway env/secret. Every query scoped by `household_id`. Supabase RLS as defense in depth.
- Money: `NUMERIC(19,4)` + currency code. Never floats.

## Phase 0: Foundations (IN PROGRESS)
Done in code (local git commits in `budget-tracker-api` and `budget-tracker-web`, not yet pushed):
- [x] Spring Boot skeleton (Java 21, Maven), layered packages, health endpoint, Flyway baseline, Testcontainers smoke test, Dockerfile. **Not compiled or tested yet**: this session's network policy blocks Maven Central, so dependencies can't resolve. First `mvn verify` will happen in GitHub Actions CI once pushed.
- [x] Next.js skeleton, Tailwind, dashboard shell. Lint, typecheck and build pass locally. (shadcn/ui deferred to Phase 1, when real screens need it.)
- [x] GitHub Actions CI workflows and PR template written in both repos
- [x] `.env.example` in both repos documenting every variable

Needs you (cannot be done from this session):
- [ ] Create empty GitHub repos `budget-tracker-api` and `budget-tracker-web`, push both local repos, enable branch protection on `main` (require CI)
- [ ] Supabase: create dev and prod projects; give me the pooler connection string format (never paste passwords into chat; set them directly in Railway)
- [ ] Railway: new service from the API repo (Dockerfile), set env vars from `.env.example`, health check `/actuator/health`
- [ ] Vercel: import the web repo, set env vars from `.env.example`
- [ ] Teller: create developer account, get application id, download client cert + key, base64 them into Railway env; confirm your banks appear in Teller Connect (sandbox first, then development)
- Verify: both apps deployed, health check reachable, CI green; first `mvn verify` run green; one Teller sandbox enrollment succeeds

## Phase 1: Auth + households (BUILT, awaiting first real build/deploy)

Status: code written in both repos. Frontend lint, typecheck and build pass. Backend NOT compiled or run by me (Maven Central is blocked in my workspace); only the framework-free domain classes were syntax-checked with javac. First real build = your local `mvn verify` or CI.

Changes from the spec: the first-household owner is identified by **Supabase user id** (`BOOTSTRAP_OWNER_USER_ID`), not email, because with email confirmation off anyone can sign up with someone else's email. shadcn/ui was skipped (its CLI needs registry access I don't have); plain Tailwind components are used. Route protection is a client-side gate; the API is the real enforcement.

Decisions: email + password only; invite by shareable link; email confirmation off for now (rate limits); account creation limited to people holding an invite (enforced in the API, see below).

### Design
- **Token validation:** Spring OAuth2 resource server verifies the Supabase JWT via JWKS (`SUPABASE_JWKS_URI`), checks issuer (`SUPABASE_ISSUER`) and audience `authenticated`. Confirmed: the project's current key is ECC (P-256), so `ES256`.
- **Users:** `app_user(id uuid = Supabase auth subject, email, display_name, created_at)`, provisioned on first authenticated request.
- **Households:** `household(id, name, created_at)`, `household_member(user_id PK, household_id, joined_at)`. One household per user (user_id is the primary key). All members have equal rights, so no role column yet.
- **Invites:** `household_invite(id, household_id, token_hash, created_by, expires_at, used_at, used_by, revoked_at)`. Raw token is shown once in the link and only its SHA-256 hash is stored. Single-use (one conditional UPDATE), expires in 7 days, revocable.
- **Invite-only enforcement:** Supabase signup stays enabled (disabling it would block the partner's own signup), but the API treats a signed-in user with no household as having no access except `GET /api/me` and `redeem invite`. The very first household can only be created by the user id in `BOOTSTRAP_OWNER_USER_ID` (set in Railway). Honest limitation: strangers can still create Supabase auth accounts, they just cannot do or see anything.
- **Central scoping (SOLID):** a `CurrentUserProvider` port with a JWT implementation and a local-profile implementation (fixed dev user and household). Controllers take a resolved `HouseholdContext`; no controller reads the JWT directly. Every later query takes `householdId` from it.
- **Local profile:** auth stays off, but a seeder creates one fixed dev user + household so household scoping is still exercised.

### Backend tasks
- [ ] Flyway `V2`: `app_user`, `household`, `household_member`, `household_invite` (in schema `budget`)
- [ ] Add `spring-boot-starter-oauth2-resource-server`; replace `SecurityConfig` with JWT validation
- [ ] `CurrentUserProvider` + `HouseholdContext` argument resolver; JIT user provisioning
- [ ] Endpoints: `GET /api/me`, `POST /api/households` (bootstrap only), `POST /api/households/invites`, `GET /api/households/invites`, `DELETE /api/households/invites/{id}`, `POST /api/invites/redeem`
- [ ] Local seeder for dev user/household
- [ ] Tests: no token -> 401; user A cannot read household B; invite single-use / expired / revoked; only `BOOTSTRAP_OWNER_EMAIL` can bootstrap; token stored hashed

### Frontend tasks
- [ ] `@supabase/supabase-js` + `@supabase/ssr`; sign-in / sign-up pages; session refresh; route protection (Next 16 conventions, check the bundled docs first)
- [ ] API client that attaches the access token
- [ ] `/join?token=...` page (sign up or sign in, then redeem)
- [ ] Household page: members list, create / copy / revoke invite link
- [ ] Local mode: when Supabase env vars are empty, skip sign-in and call the local API directly
- [ ] shadcn/ui initialized for forms and buttons

### Needs from you
- [ ] Confirm the Supabase project uses asymmetric JWT signing keys (JWT Keys page); if it shows only a legacy shared secret, tell me and I'll switch validation
- [ ] Railway env vars: `SUPABASE_JWKS_URI`, `SUPABASE_ISSUER`, `BOOTSTRAP_OWNER_EMAIL`
- [ ] Supabase Auth settings: decide whether email confirmation is required (default on; the built-in sender is rate limited)

- Verify: unauthenticated calls rejected; user A cannot read household B (integration tests); you sign up, bootstrap, invite your partner, partner joins via link, both see the same household
- Constraint: Maven Central is blocked in my workspace, so the backend can't be compiled or tested by me. CI and your local `mvn verify` are the first real build; paste any errors and I'll fix them.

## Phase 2: Connect/remove accounts (Teller)
- [ ] Teller Connect in the frontend (sandbox first); send enrollment + access token to the backend
- [ ] Backend stores the encrypted token, fetches accounts, saves bank accounts and credit cards
- [ ] Add/remove: remove = disconnect from Teller + soft-delete (transaction history policy: see open questions)
- [ ] Accounts screen: institution, type, last 4, balance, last synced
- Verify: connect a sandbox bank, see accounts, remove it; then repeat in development with a real account

## Phase 3: Transaction sync engine
- [ ] `transactions` schema + upsert by provider id; handle pending -> posted
- [ ] `sync_jobs` queue, worker, retries with backoff, per-account status
- [ ] On-demand sync endpoint + "Sync now" button with progress/status
- [ ] Nightly scheduled sync (ShedLock), time in your timezone
- [ ] Handle Teller "disconnected / re-auth needed" states in the UI
- Verify: duplicate-safe re-runs; nightly job fires once with 2 instances; failure shows in the UI

## Phase 4: Categories + rules
- [ ] Default category set; user-defined categories (name, color, icon); delete with reassignment
- [ ] Map Teller category -> your category on import
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
- [ ] Decide on Teller production (billing, approval) vs staying on development

## Resolved decisions
- Internal transfers and credit card payments are excluded from money in/out (strategy below).
- Removing an account: its transactions leave the main dataset and move to an analytics table.
- Nightly sync at 2:00 AM America/New_York.
- All household members see everything (no private accounts).
- USD only (single currency, no FX handling).
- Unused budget rolls over to the next month.
- Institution coverage: verify in Phase 0 against Teller's institution list; fall back to another provider if missing (the `BankDataProvider` interface keeps this cheap).

## Transfer / credit card payment strategy (Phase 3 detection, Phase 6 consumption)
Principle: never delete or hide data. Flag and link it, so every decision is reversible and auditable.
1. **Hints (low confidence):** Teller type/category and description patterns (e.g. "payment", "autopay", "transfer"). Raises confidence only; never decides alone. Validate the real field values against development-environment data.
2. **Pair matching (the core rule):** for each new posted transaction, look in the *other* household accounts for one with the opposite sign, same absolute amount, within +/-3 days. Exactly one candidate -> auto-link both as a transfer pair (shared `transfer_group_id`, `is_internal_transfer = true`). Several candidates -> "needs review" queue. This covers bank -> card payments and bank -> bank moves with the same logic.
3. **Only one side is connected** (e.g. paying a card you haven't linked): it is real money leaving your tracked accounts, so it stays as an outflow in a system category "Card Payment (unlinked)". Linking the other account later re-runs matching and fixes history.
4. **Pending -> posted:** match on posted transactions only; re-run matching when a pending item posts or its amount changes.
5. **User override:** mark/unmark any transaction as a transfer; a rule action "treat as transfer"; manual overrides always beat auto-matching.
6. **Consumption:** all analytics, budgets and "money in/out" queries filter `is_internal_transfer = false`. System category "Transfer" is excluded from budgets. Matching is idempotent and re-runnable.
7. Tests: exact pair, ambiguous pair (two equal amounts), date drift, one-sided, refund vs transfer, re-run idempotency.

## Account removal design (Phase 2)
- Teller access tokens are per *enrollment* (one bank login, possibly several accounts). Removing one account must not revoke the token while other accounts under it remain; revoke/delete the enrollment only when its last account is removed.
- On removal, in one DB transaction: copy the account's transactions into `analytics_transactions` (denormalized: date, amount, category name, merchant, institution, last4, household_id, `removed_account_label`), delete them from `transactions`, mark the account `removed`. Transfer links involving that account are converted so the surviving side is not left half-linked (surviving side is reclassified per strategy item 3).
- Needs a backfill-safe, resumable job for large accounts.

## Budget rollover design (Phase 5)
- Per category: `available(month) = budget(month) + carryover(prev month)`, where `carryover = available(prev) - spent(prev)`.
- Computed on read from transactions + budgets (no stored running balance), so edits to past transactions stay consistent.

## Remaining open questions
1. Year-start reset of rollover balances: not answered yet. Plan currently assumes **no reset** (balances carry across years). Say if you want a January reset.

## Final decisions (round 3)
- Overspending carries forward as a negative balance and reduces next month's available amount.
- Dashboard and budget history include data from removed accounts (read from `analytics_transactions` unioned with `transactions` via a single view/query layer, so callers don't care where rows live).
- Household members have equal rights (connect/remove accounts, edit budgets, categories, rules).
