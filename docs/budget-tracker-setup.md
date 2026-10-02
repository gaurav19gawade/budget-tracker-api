# Budget Tracker: Setup Guide

Everything needed to set up the project from scratch, in the order we did it.

**Stack:** Next.js (Vercel) -> Spring Boot API (Railway) -> Postgres (Supabase). Login via Supabase Auth. Bank data via SimpleFin Bridge.

**Repos:** `budget-tracker-api` (Java 21, Spring Boot 3.5, Maven) and `budget-tracker-web` (Next.js, TypeScript, Tailwind).

---

## 1. Prerequisites (local machine)

- Git, Docker (running), Java 21, Maven 3.9+, Node 22+ and npm
- Accounts: GitHub, Supabase, Railway, Vercel, SimpleFin Bridge

## 2. GitHub

1. Create two empty repos: `budget-tracker-api` and `budget-tracker-web`.
2. Push each project to its repo (`main` branch).
3. Branch protection on `main`: require the CI check to pass before merging.
4. CI (GitHub Actions) is already in each repo under `.github/workflows/ci.yml`:
   - API: `mvn -B verify` (Testcontainers needs Docker, which GitHub runners have).
   - Web: `npm ci`, lint, typecheck, build.

## 3. Supabase (database + login)

1. Create a project (use separate dev and prod projects if you want a clean split).
2. Get the **database connection string** (Connect -> Direct / pooler). Use the pooler host, for example `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require`. Note the database user and password.
3. Get the **API values** for the frontend (Connect -> Framework, or Project Settings -> API Keys):
   - Project URL: `https://<ref>.supabase.co`
   - **Publishable key**: `sb_publishable_...` (this is what used to be called the "anon key")
4. Never put the **secret key** (`sb_secret_...`), the database password, or the token encryption key in the frontend or in Vercel.

### Why the frontend needs the Supabase URL

It is only for **Supabase Auth** (sign-in). The browser signs in with Supabase and gets a token (JWT). It sends that token to the API, and the API verifies it. All database access goes through the API; the frontend never talks to the database.

## 4. Backend on Railway

1. New service from the `budget-tracker-api` GitHub repo. Railway builds from the `Dockerfile`.
2. Set these variables:

| Variable | Value |
|---|---|
| `DATABASE_URL` | the JDBC pooler URL from step 3 |
| `DATABASE_USERNAME` | database user |
| `DATABASE_PASSWORD` | database password (set directly in Railway, never in chat or git) |
| `DB_SCHEMA` | `budget` (optional, this is the default) |
| `APP_CORS_ALLOWED_ORIGINS` | your Vercel URL, e.g. `https://budget-tracker-web.vercel.app` (comma-separated for several) |
| `SUPABASE_JWKS_URI` | `https://<ref>.supabase.co/auth/v1/.well-known/jwks.json` |
| `SUPABASE_ISSUER` | `https://<ref>.supabase.co/auth/v1` |
| `SUPABASE_JWS_ALGORITHM` | optional; `ES256` is the default and matches an ECC (P-256) signing key |
| `BOOTSTRAP_OWNER_USER_ID` | your Supabase user id; set it after your first sign-up (see "First-time owner setup") |
| `TOKEN_ENCRYPTION_KEY` | AES-256 key for encrypting SimpleFin access URLs at rest. Generate: `openssl rand -base64 32` |

### First-time owner setup

1. Deploy the API and the web app with the variables above (leave `BOOTSTRAP_OWNER_USER_ID` empty for now).
2. Open the web app and create your account. You will land on a "Join your household" page that shows your **user id**.
3. Copy that id into `BOOTSTRAP_OWNER_USER_ID` in Railway and let it redeploy.
4. Reload the app and click **Create household**.
5. Go to **Household** -> **Create invite link**, send the link to your partner. They create an account, open the link and click **Join household**.

Why the owner is identified by user id and not by email: with email confirmation off, anyone can sign up with any email address, so an email check could be claimed by someone else. A user id cannot be.

Note: strangers can still create a Supabase login (signup stays open so your partner can sign up), but without a household they cannot see or do anything in the app.

3. Health check path: `/actuator/health`. Verify: `https://<your-railway-url>/actuator/health` returns `{"status":"UP"}`.
4. Do **not** set the `local` profile on Railway. It disables authentication.

### Known issue we hit: Flyway on Supabase

Error: `Found non-empty schema(s) "public" but no schema history table`.

- **Why:** Supabase pre-populates the `public` schema, so Flyway won't initialize it.
- **Fix:** the app uses its own `budget` schema. In `application.yml`: `spring.flyway.schemas`, `spring.flyway.default-schema` and `spring.jpa.properties.hibernate.default_schema` are all `${DB_SCHEMA:budget}`, and `spring.flyway.create-schemas` is `true`.
- **Why not `baselineOnMigrate`:** it would skip the first migration, and Supabase's REST API exposes `public` by default, so tables there without row-level security could be readable with the public key.
- The database user needs permission to create schemas (the default `postgres` user has it). In native SQL, qualify tables as `budget.table_name`.

## 5. Frontend on Vercel

1. Import the `budget-tracker-web` repo. Framework preset: Next.js. Root directory: `./`.
2. Environment variables (Production and Preview). Vercel blocks `NEXT_PUBLIC_` prefixed names — instead set non-prefixed names and `next.config.ts` maps them to `NEXT_PUBLIC_*` at build time:

| Variable | Value |
|---|---|
| `API_BASE_URL` | Railway URL, no trailing slash, e.g. `https://budget-tracker-api.up.railway.app` |
| `SUPABASE_PROJECT_URL` | `https://<ref>.supabase.co` |
| `SUPABASE_PUBLISHABLE_KEY` | `sb_publishable_...` |

3. Deploy. Then set `APP_CORS_ALLOWED_ORIGINS` in Railway to the Vercel URL, or browser calls to the API will be blocked.

## 6. SimpleFin Bridge (bank data)

We replaced Teller (which shut down) with [SimpleFin Bridge](https://beta-bridge.simplefin.org/info/developers).

**How it works:**
1. Create an application at `beta-bridge.simplefin.org`. Each application gets one pool of connections.
2. In the app, click **Connect account** to open the two-step flow:
   - **Step 1:** Visit `beta-bridge.simplefin.org/create`, connect your financial institution, and copy the setup token.
   - **Step 2:** Paste the token into the app and click **Connect**.
3. The backend exchanges the token for a persistent access URL (one-time claim), fetches accounts and balances, and stores the encrypted access URL.

**Limits:**
- The beta tier allows approximately **24 requests per day** (~1 per hour) to the accounts endpoint.
- Balances are only refreshed when explicitly requested — there is no push/webhook.
- The token encryption key (`TOKEN_ENCRYPTION_KEY`) is already set from the initial setup.

**No additional Railway or Vercel env vars are needed** for SimpleFin beyond what's already set.

## 7. Run everything locally (production-like, no cloud services)

Local uses plain Postgres 17 in Docker and the `local` Spring profile, which turns **authentication off**. A guard (`LocalProfileGuard`) refuses to start if that profile is used with a non-localhost database.

**Terminal 1: backend**
```bash
cd budget-tracker-api
docker compose up -d            # Postgres on localhost:5433
mvn spring-boot:run             # "local" profile is on by default for this command only
curl localhost:8080/actuator/health
```

**Terminal 2: frontend**
```bash
cd budget-tracker-web
cp .env.example .env.local      # NEXT_PUBLIC_API_BASE_URL is already http://localhost:8080
npm install
npm run dev                     # http://localhost:3000
```

Notes:
- `pom.xml` sets `spring-boot.run.profiles=local`, which applies to `mvn spring-boot:run` only. The Docker image, Railway and `mvn verify` do not get it, so production never runs with auth off.
- To run the real security chain locally: `mvn spring-boot:run -Dspring-boot.run.profiles=default` with the variables from `.env.example` set.
- IntelliJ: set "Active profiles" to `local` in the run configuration.
- Reset the local database: `docker compose down -v && docker compose up -d`.
- To pull env vars from Vercel instead: `npm i -g vercel`, `vercel login`, `vercel link`, `vercel env pull .env.local`. This pulls the **Development** environment, so either tick Development on each variable or use `--environment=preview`. Variables marked "Sensitive" can't be pulled.

## 8. Checks before you push

```bash
# backend
mvn verify                      # needs Docker running

# frontend
npm run lint && npm run build
```

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Flyway: non-empty schema "public" | Supabase's `public` is never empty | Dedicated `budget` schema (section 4) |
| Can't find the "anon key" in Supabase | Renamed to the publishable key | Use `SUPABASE_PUBLISHABLE_KEY` in Vercel |
| Browser blocks calls to the API | CORS doesn't allow the frontend origin | Set `APP_CORS_ALLOWED_ORIGINS` in Railway with `https://` prefix |
| Local app refuses to start with "Profile 'local'..." | `local` profile pointed at a non-localhost database | Use the Docker Postgres on `localhost:5433` |
| `vercel env pull` returns nothing | Variables only set for Production and Preview | Tick Development, or `--environment=preview` |
| Port 5432 already in use | Local Postgres already running | Compose uses host port 5433 on purpose |
| API URL treated as relative path | `API_BASE_URL` in Vercel missing `https://` scheme | Set to `https://budget-tracker-api.up.railway.app` (full URL) |
| Railway startup crash: "Failed to configure Teller mTLS client" | Old env vars with base64 newlines (Teller era, now removed) | Remove `TELLER_CERT_PEM_BASE64` and `TELLER_KEY_PEM_BASE64` from Railway |
| SimpleFin connect returns error | Setup token already used (one-time) | Go to SimpleFin Bridge and generate a new setup token |
| Email confirmation redirects to localhost | Supabase Site URL not updated | In Supabase Auth settings, set Site URL to your Vercel URL; add `https://your-app.vercel.app/**` and `http://localhost:3000/**` to Redirect URLs |

## 10. Project status

- **Phase 0 (foundations):** done — both deploys healthy, CI green.
- **Phase 1 (auth + households):** done — Supabase JWT validation, households, invite flow, partner join.
- **Phase 2 (bank accounts):** done — SimpleFin Bridge connect/remove, encrypted access URL storage, accounts page.
- **Next, Phase 3:** transaction sync engine (see `todo.md`).

## 11. Rules to remember

- Secrets (database password, `TOKEN_ENCRYPTION_KEY`) live only in Railway, never in git, chat or Vercel.
- `API_BASE_URL`, `SUPABASE_PROJECT_URL`, `SUPABASE_PUBLISHABLE_KEY` go to Vercel (not `NEXT_PUBLIC_*` — Vercel blocks that prefix; `next.config.ts` maps them).
- Never run the `local` profile against a real database.
- SimpleFin access URLs contain embedded credentials — they are encrypted at rest in the `teller_enrollment` table and never logged.
