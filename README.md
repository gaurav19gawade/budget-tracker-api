# budget-tracker-api

Backend for the household budget tracker. Java 21, Spring Boot 3.5, Maven, Postgres (Supabase), deployed on Railway.

## Layout (dependencies point inward)

```
api            controllers + DTOs
application    use cases and ports
domain         pure business model/rules
infrastructure JPA, Teller client, schedulers, config
```

## Auth and households (Phase 1)

- Requests carry a Supabase access token. The API verifies it against Supabase's public keys (JWKS), the issuer and the `authenticated` audience (`SecurityConfig`).
- `CurrentUserArgumentResolver` is the one place that turns the token into a user and a household. Controllers receive `AuthenticatedPrincipal` or `HouseholdContext`; every household-scoped query must use `HouseholdContext.householdId()`.
- A user with no household can only call `GET /api/me`, `POST /api/households` (owner only) and `POST /api/invites/redeem`; everything else returns 403 `NO_HOUSEHOLD`.

| Endpoint | Who | Purpose |
|---|---|---|
| `GET /api/me` | any signed-in user | profile, household, members, `canCreateHousehold` |
| `POST /api/households` | `BOOTSTRAP_OWNER_USER_ID` only, once | create the first household |
| `POST /api/households/invites` | household member | new single-use invite (raw token returned once) |
| `GET /api/households/invites` | household member | list invites and status |
| `DELETE /api/households/invites/{id}` | household member | revoke |
| `POST /api/invites/redeem` | signed-in user without household | join with a token |

Invite tokens are stored only as SHA-256 hashes, are single-use (one conditional UPDATE), and expire after 7 days.
In the `local` profile there is no token: a fixed dev user and household are seeded at startup.

## Run locally (production-like, no cloud services)

Prereqs: Docker, Java 21, Maven. Uses plain Postgres 17 in Docker and the `local` profile, which **disables authentication** (a guard refuses to start if the database isn't on localhost).

```bash
docker compose up -d                       # Postgres on localhost:5433
mvn spring-boot:run                        # the "local" profile is on by default for this command only
curl localhost:8080/actuator/health        # {"status":"UP"}
```

`pom.xml` sets `spring-boot.run.profiles=local`, so `mvn spring-boot:run` loads `application-local.yml` automatically. The packaged jar (Docker/Railway) and `mvn verify` do not, so production never gets the no-auth profile by accident. In IntelliJ, set "Active profiles" to `local` in the run configuration.

Flyway creates the `budget` schema and runs migrations on startup, exactly as in production.
Then run the frontend from `budget-tracker-web` (`npm run dev`, with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`).

Reset the local database: `docker compose down -v && docker compose up -d`.

To run the real (non-local) security chain locally, run `mvn spring-boot:run -Dspring-boot.run.profiles=default` with the variables from `.env.example` set.

## Tests

```bash
mvn verify   # Testcontainers starts Postgres; Docker must be running
```

## Deploy (Railway)

Railway builds from the `Dockerfile`. Set the variables from `.env.example`. Health check path: `/actuator/health`.
